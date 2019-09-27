package io.directus.tools;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;


/**
 * 
 * Directus Administration SDK provides a set of helper methods to create and maintain collections
 * 
 * This is messy because we brutally decompiled it from Kotlin to Java and cleaned it up just a tad
 * 
 */
public class DirectusAdministrationSdk {

    private final Logger logger = Logger.getLogger(DirectusAdministrationSdk.class.getName());

    private String apiBaseUrl;
    private String token;
    private String projectName;

    /**
     * uses the default project ("_")
     *
     * @param apiBaseUrl  the base url to the directus api including scheme
     * @param token and admin token (e.g. a bearer token or onr set for an admin user)
     */
    public DirectusAdministrationSdk(String apiBaseUrl, String token) {
        this(apiBaseUrl, "_", token);
    }

    /**
     * @param apiBaseUrl  the base url to the directus api including scheme
     * @param token and admin token (e.g. a bearer token or onr set for an admin user)
     * @param projectPath the projects sub path
     */
    public DirectusAdministrationSdk(String apiBaseUrl, String projectName, String token) {
        this.apiBaseUrl = apiBaseUrl;
        this.projectName = projectName;
        this.token = token;
    }


    /**
     * general get method to retrieve an object from some URL
     */
    public JSONObject getDirectusData(String urlContext) throws IOException, InterruptedException {
        Objects.requireNonNull(urlContext, "urlContext");
        HttpRequest request = httpRequestBuilder(urlContext).GET().build();
        HttpResponse<String> response = sendHttpRequest(request);
        if (response.statusCode() > 400) {
            throw (new IllegalStateException("Error thrown when executing request '" + request.uri() + "' response: '" +
                response + '/' + (String) response.body() + '\''));
        }

        return new JSONObject((String) response.body());
    }


    /**
     * get the Directus field definition of a collection's field
     */
    public JSONObject getFieldDefinition(String collectionName, String fieldName)
                    throws IOException, InterruptedException {
        Objects.requireNonNull(collectionName, "collection");
        Objects.requireNonNull(fieldName, "field");
        JSONObject directusObject = getDirectusData("/fields/" + collectionName + '/' + fieldName);
        if (!directusObject.has("data")) {
            throw new IllegalStateException("Failed to retrieve field definition for collection '" + collectionName +
                "' field '" + fieldName + "': no data exists");
        }

        JSONObject directusData = directusObject.getJSONObject("data");
        if (directusData.get("id") == null) {
            throw (new IllegalStateException("Failed to retrieve field definition for collection '" + collectionName +
                "' field '" + fieldName + "' with returned data: '" + directusData + '\''));
        }
        List.of("collection", "id", "group").forEach(directusData::remove);
        return directusData;
    }


    private void removeProblematicFieldDefinitionDefaults(JSONObject fieldDef) {
        if ("TEXT".equalsIgnoreCase(fieldDef.getString("datatype"))) {
            fieldDef.remove("length");
        }
    }


    private JSONObject createFieldDefFromOtherField(String collection, String newField,
        JSONObject oldFieldDefinition) throws IOException, InterruptedException {
        JSONObject newFieldDef = new JSONObject(oldFieldDefinition.toString());
        removeProblematicFieldDefinitionDefaults(newFieldDef);
        newFieldDef.put("field", newField);
        HttpRequest request = httpRequestBuilder("/fields/" + collection)
            .POST(BodyPublishers.ofString(newFieldDef.toString()))
            .build();
        HttpResponse<String> response = sendHttpRequest(request);
        if (response.statusCode() > 400) {
            throw (new IllegalStateException("Error thrown when executing request '" + request + "' response: '" +
                response + '/' + (String) response.body() + '\''));
        }

        JSONObject directusData = (new JSONObject((String) response.body())).getJSONObject("data");
        if (!newField.equals(directusData.get("field"))) {
            throw (new IllegalStateException(
                "Failed to create field definition forcollection '" + collection + "' field '" + newField + "'." +
                    " Old field def : '" + oldFieldDefinition + "'. Returned data: '" + directusData + '\''));
        }
        return directusData;
    }


    public HttpResponse<String> updateValue(String collection, long id, String fieldName,
        Object fieldValue) throws IOException, InterruptedException {
        Objects.requireNonNull(collection, "collection");
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(fieldValue, "fieldValue");
        JSONObject updateValue = (new JSONObject()).put(fieldName, fieldValue);
        HttpRequest request = httpRequestBuilder("/items/" + collection + '/' + id)
            .method("PATCH", BodyPublishers.ofString(updateValue.toString()))
            .build();
        HttpResponse<String> response = sendHttpRequest(request);
        return response;
    }

    /**
     * post the data from the json file to the colection item endpoint
     */
    public void addData(String collectionName, File dataFile) throws IOException, InterruptedException {
        Objects.requireNonNull(dataFile, "dataFile");

        String collectionSchema = Files.readString(dataFile.toPath());
        logger.info("inserting data into '" + collectionName + "' using " + dataFile.getAbsolutePath());

        HttpRequest request = httpRequestBuilder("/items/" + collectionName)
            .method("POST", BodyPublishers.ofString(collectionSchema.toString()))
            .build();
        HttpResponse<String> response = sendHttpRequest(request);

        if (response == null || response.statusCode() != 200) {
            throw new IllegalStateException(
                "inserting data '" + collectionName + "' failed. Response: '" + response +
                    '/' + (String) response.body() + '\'');
        }

        logger.info("inserted data into " + collectionName + '\'');
    }

    public void copyDataFromFieldToField(String collection, String oldFieldName, String newFieldName)
                    throws IOException, InterruptedException {
        Objects.requireNonNull(collection, "collection");
        Objects.requireNonNull(oldFieldName, "oldFieldName");
        Objects.requireNonNull(newFieldName, "newFieldName");

        JSONArray existingItems =
            getDirectusData("/items/" + collection + "?fields=id," + oldFieldName).getJSONArray("data");

        for (Object object : existingItems) {
            if (!(object instanceof JSONObject)) {
                throw (new IllegalStateException("'" + object + "' is not the expected JSONObject type "));
            }

            JSONObject jsonObj = (JSONObject) object;
            if (jsonObj.has(oldFieldName) && !jsonObj.isNull(oldFieldName)) {

                long id = jsonObj.getLong("id");
                Object existingValue = jsonObj.get(oldFieldName);

                HttpResponse<String> updateResponse = updateValue(collection, id, newFieldName, existingValue);
                if (updateResponse == null || updateResponse.statusCode() != 200) {
                    throw (new IllegalStateException(
                        "update for field '" + newFieldName + "' in collection '" + collection + "' failed for " +
                            jsonObj + ". Response:: " + updateResponse));
                }
            }
        }
    }


    /**
     * originally this was using gihub diffutils but it increases the jar size to
     * 
     * @param collectionName
     * @param before
     * @param after
     * @throws IOException
     * @throws DiffException
     */
    private void checkForDifferences(String collectionName, JSONObject before, JSONObject after)
                    throws IOException {
        String beforeString = before.toString(2);
        String afterString = after.toString(2);

        String dateString = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"));

        File beforeFile =
            File.createTempFile("patch." + collectionName + '.' + dateString + ".before.", ".tmp", new File("."));
        Files.writeString(beforeFile.toPath(), beforeString);

        File afterFile =
            File.createTempFile("patch." + collectionName + '.' + dateString + ".after.", ".tmp", new File("."));
        Files.writeString(afterFile.toPath(), afterString);

        List<String> listBefore = Files.readAllLines(beforeFile.toPath(), Charset.defaultCharset());
        List<String> listAfter = Files.readAllLines(afterFile.toPath(), Charset.defaultCharset());

        if (listBefore.equals(listAfter)) {
            logger.info("No changes");
            beforeFile.delete();
            afterFile.delete();
        } else {
            logger
                .info("Changes were applied check these files for more details: '" + beforeFile.getPath() + "' and '" +
                    afterFile.getPath() +
                    '\'');
        }
    }


    public void patch(File schemaFile) throws IOException, InterruptedException {
        Objects.requireNonNull(schemaFile, "schemaFile");

        String collectionSchema = Files.readString(schemaFile.toPath());
        String collectionName = readCollectionNameFromSchema(schemaFile, collectionSchema);

        logger.info("patching collection '" + collectionName + "' using " + schemaFile.getAbsolutePath());

        JSONObject before = getDirectusData("/collections/" + collectionName);

        HttpRequest request = httpRequestBuilder("/collections/" + collectionName)
            .method("PATCH", BodyPublishers.ofString(collectionSchema.toString()))
            .build();
        HttpResponse<String> response = sendHttpRequest(request);

        if (response == null || response.statusCode() != 200) {
            throw new IllegalStateException(
                "patching collection '" + collectionName + "' failed. Response: '" + response +
                    '/' + (String) response.body() + '\'');
        }

        JSONObject after = getDirectusData("/collections/" + collectionName);
        checkForDifferences(collectionName, before, after);
        logger.info("patched collection '" + collectionName + '\'');
    }


    public void createCollection(File schemaFile) throws IOException, InterruptedException {
        Objects.requireNonNull(schemaFile, "schemaFile");

        String collectionSchema = Files.readString(schemaFile.toPath());
        String collectionName = readCollectionNameFromSchema(schemaFile, collectionSchema);

        logger.info("creating collection '" + collectionName + "' using " + schemaFile.getAbsolutePath());

        HttpRequest request = httpRequestBuilder("/collections")
            .method("POST", BodyPublishers.ofString(collectionSchema.toString()))
            .build();
        HttpResponse<String> response = sendHttpRequest(request);

        if (response == null || response.statusCode() != 200) {
            throw new IllegalStateException(
                "creating collection '" + collectionName + "' failed. Response: '" + response +
                    '/' + (String) response.body() + '\'');
        }

        logger.info("created collection '" + collectionName + '\'');
    }


    /**
     * drops the collections with the passed name
     * This first checks if the collection actually exists
     * @param collectionName
     * @throws IOException
     * @throws InterruptedException
     */
    public void dropCollectionIfExists(String collectionName) throws IOException, InterruptedException {
        Objects.requireNonNull(collectionName, "collectionName");

        logger.info("dropping collection '" + collectionName + "'  including all data.");

        // check if the collection exists to make this rerunnable
        HttpResponse<String> response = sendHttpRequest(httpRequestBuilder("/collections/" + collectionName).GET().build());
        if (response.statusCode() == 404) {
            logger.info("collection '" + collectionName + "' does not exist.");
            return;
        }

        HttpRequest request = httpRequestBuilder("/collections/" + collectionName)
            .DELETE()
            .build();
        response = sendHttpRequest(request);

        if (response == null || response.statusCode() != 204) {
            throw new IllegalStateException(
                "dropping collection '" + collectionName + "' failed. Response: '"
                        + response + '/' + (String) response.body() + '\'');
        }

        logger.info("dropped collection '" + collectionName + '\'');
    }


    public void dropField(String collectionName, String fieldName) throws IOException, InterruptedException {
        Objects.requireNonNull(collectionName, "collection");
        Objects.requireNonNull(fieldName, "field");
        logger.info("dropping field '" + fieldName + "' from collection '" + collectionName + '\'');

        HttpRequest request = httpRequestBuilder("/fields/" + collectionName + '/' + fieldName).DELETE().build();

        getFieldDefinition(collectionName, fieldName);
        HttpResponse<String> response = sendHttpRequest(request);

        if (response == null || (response.statusCode() != 204 && response.statusCode() != 404)) {
            throw new IllegalStateException("deleting field '" + fieldName + "' from collection '" + collectionName +
                "' failed response: '" + response + '/' + (String) response.body() + '\'');
        }
        logger.info("dropped field '" + fieldName + "' from collection '" + collectionName + '\'');
    }


    public void createM2ORelation(String manyCollection, String manyField, String oneCollection,
        String fieldOne) throws IOException, InterruptedException {
        Objects.requireNonNull(manyCollection, "manyCollection");
        Objects.requireNonNull(manyField, "manyField");
        Objects.requireNonNull(oneCollection, "oneCollection");
        logger.info("creating relation for manyCollection: '" + manyCollection + "', manyField: '" + manyField +
            "', oneCollection: '" + oneCollection + "', fieldOne: '" + fieldOne + '\'');

        JSONObject newRelation = new JSONObject();
        newRelation.put("collection_many", manyCollection);
        newRelation.put("field_many", manyField);
        newRelation.put("collection_one", oneCollection);
        if (fieldOne != null) {
            newRelation.put("field_one", fieldOne);
        }

        HttpRequest request = httpRequestBuilder("/relations")
            .POST(BodyPublishers.ofString(newRelation.toString()))
            .build();
        HttpResponse<String> response = sendHttpRequest(request);

        if (response == null || response.statusCode() != 200) {
            throw new IllegalStateException("created relation for manyCollection: '" + manyCollection + '\'' +
                ", manyField: '" + manyField + "', oneCollection: '" + oneCollection + "', fieldOne: '" + fieldOne +
                "'\"." + "failed response: '" + response + '/' + (String) response.body() + '\'');
        }

        logger.info("created relation for manyCollection: '" + manyCollection + "', manyField: '" + manyField +
            "', oneCollection: '" + oneCollection + "', fieldOne: '" + fieldOne + '\'');
    }


    public void deleteM2ORelation(String manyCollection, Object manyField, String oneCollection,
        String fieldOne) throws IOException, InterruptedException {
        Objects.requireNonNull(manyCollection, "manyCollection");
        Objects.requireNonNull(manyField, "manyField");
        Objects.requireNonNull(oneCollection, "oneCollection");
        logger.info("deleting relations for manyCollection: '" + manyCollection + "', manyField: '" + manyField +
            "', oneCollection: '" + oneCollection + "', fieldOne: '" + fieldOne + '\'');

        JSONObject directusObject = getDirectusData("/relations?fields=id&filter[collection_many][eq]=" +
            manyCollection + "&filter[field_many][eq]=" + manyField + "&filter[collection_one][eq]=" + oneCollection +
            (fieldOne == null ? "&filter[field_one][null]=1"
                            : "&filter[field_one][eq]=" + fieldOne + "&filter[junction_field][null]=1"));

        if (!directusObject.has("data") || directusObject.getJSONArray("data").isEmpty()) {
            logger.severe("Couldn't find entry for relations for manyCollection: '${manyCollection}'," +
                " manyField: '${manyField}', oneCollection: '${oneCollection}', fieldOne: '${fieldOne}'");
            return;
        }

        JSONArray existingRelations = directusObject.getJSONArray("data");

        for (Object object : existingRelations) {
            if (!(object instanceof JSONObject)) {
                throw (new IllegalStateException("'" + object + "' is not the expected JSONObject type "));
            }
            long id = ((JSONObject) object).getLong("id");

            // deleting hangs for the time we set as timeout
            // https://bugs.openjdk.java.net/browse/JDK-8211437
            //
            logger.info("deleting relation '${id}'");

            HttpRequest request = httpRequestBuilder("/relations/" + id).DELETE().build();
            HttpResponse<String> response = sendHttpRequest(request);
            if (response == null || response.statusCode() != 204) {
                throw new IllegalStateException("deleting relation id = '" + id + "' failed. Response: '" + response +
                    '/' + (String) response.body() + '\'');
            }

            logger.info("deleted relation with id '" + id + '\'');
        }
    }


    public void renameField(String collectionName, String oldFieldName, String newFieldName)
                    throws IOException, InterruptedException {
        Objects.requireNonNull(collectionName, "collection");
        Objects.requireNonNull(oldFieldName, "oldFieldName");
        Objects.requireNonNull(newFieldName, "newFieldName");
        logger.info("renaming field '" + oldFieldName + "' in collection '" + collectionName + "' to name '" +
            newFieldName + '\'');


        JSONObject fieldDef = getFieldDefinition(collectionName, oldFieldName);
        logger.info("creating '" + newFieldName + "' in collection '" + collectionName + '\'');
        createFieldDefFromOtherField(collectionName, newFieldName, fieldDef);
        logger.info("moving data from '" + oldFieldName + "' to '" + newFieldName + '\'');
        copyDataFromFieldToField(collectionName, oldFieldName, newFieldName);
        dropField(collectionName, oldFieldName);
        logger.info("rename complete");
    }


    private Builder httpRequestBuilder(String context) {
        return HttpRequest.newBuilder().timeout(Duration.ofSeconds(60L))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + token)
            .uri(URI.create(apiBaseUrl + "/" + projectName + context));
    }


    private HttpResponse<String> sendHttpRequest(HttpRequest request) throws IOException, InterruptedException {
        return HttpClient.newHttpClient().send(request, BodyHandlers.ofString());
    }


    /**
     * takes a String of a collection json and attempts to retrieve the collection name or bombs out
     * 
     * @param schemaFile
     * 
     * @param collectionSchema
     * @return
     */
    private String readCollectionNameFromSchema(File schemaFile, String collectionSchema) {
        JSONObject schemaDef = new JSONObject(collectionSchema);
        if (!schemaDef.has("collection")) {
            throw new IllegalStateException(
                "Cannot find collection attribute in passed schema file " + schemaFile.getAbsolutePath());
        }
        return schemaDef.getString("collection");
    }

}
