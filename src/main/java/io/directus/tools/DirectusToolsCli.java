package io.directus.tools;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * quick and dirty wrapper around the admin sdk
 */
public class DirectusToolsCli {

    private final Map<String, CliCommandMethodInfo> COMMAND_METHODS;
    private DirectusAdministrationSdk sdk;

    private DirectusToolsCli() {
        COMMAND_METHODS = Arrays
            .stream(DirectusToolsCli.class.getDeclaredMethods())
            .filter(m -> (m.getAnnotation(CliCommandInfo.class) != null))
            .map(m -> new CliCommandMethodInfo(m.getAnnotation(CliCommandInfo.class), m))
            .collect(
                Collectors.toMap(c -> c.name, c -> c));
    }


    /**
     * Apache CLI lib not really help because of the different options we would have for different
     * commands, more complex libs might work but would blow up jar
     */
    private void print_help_and_exit() {
        System.out.println("A bunch of simple directus admin cli commands");
        System.out.println("requires DIRECTUS_API_HOST and DIRECTUS_ADMIN_TOKEN environment variables to be set");
        System.out.println();

        COMMAND_METHODS.values().forEach(c -> {
            for (String desriptionLine : c.descriptionLines) {
                System.out.println(desriptionLine);
            }
            System.out.println();
        });

        System.out.println();
        System.exit(-1);
    }


    /**
     * checks if essential environment variables are set
     */
    public static void checkEnvironmentVars() throws Exception {
        String apiBaseUrl = System.getenv("DIRECTUS_API_HOST");
        String token = System.getenv("DIRECTUS_ADMIN_TOKEN");
        if (apiBaseUrl == null || token == null) {
            if (apiBaseUrl == null) {
                System.out.println("environment variable DIRECTUS_ADMIN_TOKEN must be set");
            }

            if (token == null) {
                System.out.println("environment variable DIRECTUS_API_HOST must be set");
            }
            System.exit(-2);
        }
    }

    @CliCommandInfo(name = "api_info",
        argCount = 0,
        descriptionLines = {
            "api_info ",
            "  calls the  root path of the API to display version, name, etc."})
    private void apiInfo(List<String> arguments) throws Exception {
        System.out.println(sdk.getDirectusData("/").toString(2));
    }


    @CliCommandInfo(name = "create_collection",
        argCount = 1,
        descriptionLines = {
            "create_collection {collection.schema.json.file}",
            "   create a new collection based on {collection.schema.json.file}"})
    private void createCollection(List<String> arguments) throws Exception {
        File collectionSchemaFile = getReadableFileOrFail(arguments.get(0));
        sdk.createCollection(collectionSchemaFile);
    }


    @CliCommandInfo(name = "patch",
        argCount = 1,
        descriptionLines = {
            "patch {collection.schema.json.file}",
            "   patch an existing collection based on {collection.schema.json.file}",
            "   NOTE: this does not delete removed fields, it only adds new fields or updates existing fields, use drop_field to remove fields",
            "   NOTE: the API might not change attributes. e.g. unique fields cannot be made un-unique",
            "   NOTE: the old definitions is compared with the new definitons and if it changed, then it leaves the files for manual comparison"})
    private void patch(List<String> arguments) throws IOException, InterruptedException {
        File collectionSchemaFile = getReadableFileOrFail(arguments.get(0));
        sdk.patch(collectionSchemaFile);
    }

    @CliCommandInfo(name = "create_m2o",
        argCount = 4,
        descriptionLines = {
            "create_m2o {manyCollection} {manyField} {oneCollection} [fieldOne]",
            "  create a new M2O relation based on the passed collection and field names, {fieldOne} is optional"})
    private void createM2O(List<String> arguments) throws Exception {
        String manyCollection = arguments.get(0);
        String manyField = arguments.get(1);
        String oneCollection = arguments.get(2);
        String fieldOne = arguments.get(3);
        sdk.createM2ORelation(manyCollection, manyField, oneCollection, fieldOne);
    }


    @CliCommandInfo(name = "delete_m2o",
        argCount = 4,
        descriptionLines = {
            "delete_m2o {manyCollection} {manyField} {oneCollection} [fieldOne]",
            "  delete all M2O relations that match the passed collection and field names, {fieldOne} is optional and defaults to null"})
    private void deleteM2O(List<String> arguments) throws Exception {
        String manyCollection = arguments.get(0);
        String manyField = arguments.get(1);
        String oneCollection = arguments.get(2);
        String fieldOne = arguments.get(3);
        sdk.deleteM2ORelation(manyCollection, manyField, oneCollection, fieldOne);
    }


    @CliCommandInfo(name = "get_field_def",
        argCount = 2,
        descriptionLines = {
            "get_field_def {collectionName} {fieldName} ",
            "  get the field definition for {fieldName} in collection {collectionName}"})
    private void getFieldDefinition(List<String> arguments) throws Exception {
        String collectionName = arguments.get(0);
        String fieldName = arguments.get(1);
        System.out.println(sdk.getFieldDefinition(collectionName, fieldName).toString(2));
    }


    @CliCommandInfo(name = "drop_field",
        argCount = 2,
        descriptionLines = {
            "drop_field {collectionName} {fieldName} ",
            "  delete the field {fieldName} in collection {collectionName}"})
    private void dropField(List<String> arguments) throws Exception {
        String collectionName = arguments.get(0);
        String fieldName = arguments.get(1);
        sdk.dropField(collectionName, fieldName);
    }


    @CliCommandInfo(name = "rename_field",
        argCount = 3,
        descriptionLines = {
            "rename_field {collectionName} {currentfieldName} {newFieldName}",
            "  rename field {currentfieldName} in collection {collectionName} to {newFieldName}",
            "  NOTE: this is not working for relation fields. in the background this create a new field and copies the data over and deletes the old field"})
    private void renameField(List<String> arguments) throws Exception {
        String collectionName = arguments.get(0);
        String oldFieldName = arguments.get(1);
        String newFieldName = arguments.get(2);
        sdk.renameField(collectionName, oldFieldName, newFieldName);
    }


    @CliCommandInfo(name = "get_data",
        argCount = 1,
        descriptionLines = {
            "get_data {urlContext}",
            "  calls field {currentfieldName} in collection {urlContext}",
            "  NOTE: this is not working for relation fields. in the background this create a new field and copies the data over and deletes the old field"})
    private void getData(List<String> arguments) throws Exception {
        System.out.println(sdk.getDirectusData(arguments.get(0)).toString(2));
    }

    private void processCliArgs(String[] args) throws Exception {

        if (args.length < 1 || !COMMAND_METHODS.containsKey(args[0].toLowerCase())) {
            print_help_and_exit();
        }

        List<String> arguments = new ArrayList<String>(List.of(args));
        arguments.remove(0);

        CliCommandMethodInfo cliCommandInfoMethod = COMMAND_METHODS.get(args[0].toLowerCase());
        if (cliCommandInfoMethod.argCount != arguments.size()) {

            System.out.println("Command argument count is wrong");
            System.out.println();

            for (String descriptionLine : cliCommandInfoMethod.descriptionLines) {
                System.out.println(descriptionLine);
            }
            System.out.println();
            System.exit(-2);
        }

        checkEnvironmentVars();
        String apiBaseUrl = System.getenv("DIRECTUS_API_HOST");
        String token = System.getenv("DIRECTUS_ADMIN_TOKEN");
        sdk = new DirectusAdministrationSdk(apiBaseUrl, token);
        
        // test a basic call to see if things are working
        sdk.getDirectusData("/");

        cliCommandInfoMethod.method.invoke(this, (Object) arguments);
    }


    private File getReadableFileOrFail(String path) {
        File file = new File(path);

        if (!file.exists()) {
            System.err.println("Passed file '" + path + "' doesn't exist.");
            System.exit(-5);
        }

        if (!file.canRead()) {
            System.err.println("Cannot read file '" + path + "'.");
            System.exit(-6);
        }

        return file;
    }



    public static void main(String[] args) throws Exception {
        DirectusToolsCli directusToolsCli = new DirectusToolsCli();
        directusToolsCli.processCliArgs(args);
    }


    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface CliCommandInfo {
        String name();

        int argCount();

        String[] descriptionLines();
    }

    private class CliCommandMethodInfo {
        private final List<String> descriptionLines;
        private final int argCount;
        private final String name;
        private final Method method;

        public CliCommandMethodInfo(CliCommandInfo cliCommandInfo, Method m) {
            this.method = m;
            this.name = cliCommandInfo.name().toLowerCase();
            this.argCount = cliCommandInfo.argCount();
            this.descriptionLines = List.of(cliCommandInfo.descriptionLines());
        }
    }



}
