package de.worldoneo.joobycodegen;

import io.swagger.codegen.v3.CodegenModel;
import io.swagger.codegen.v3.CodegenOperation;
import io.swagger.codegen.v3.CodegenProperty;
import io.swagger.codegen.v3.CodegenType;
import io.swagger.codegen.v3.SupportingFile;
import io.swagger.codegen.v3.generators.java.AbstractJavaCodegen;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JoobyServerCodegen extends AbstractJavaCodegen {
    public static final String ROOT_PACKAGE = "rootPackage";
    protected String resourceFolder = "src/main/resources";
    protected String rootPackage = "org.simpleserver";
    protected String apiVersion = "1.0.0-SNAPSHOT";

    public JoobyServerCodegen() {
        super();

        // set the output folder here
        outputFolder = "generated-code" + File.separator + "javaJoobyServer";

        modelTemplateFiles.clear();
        modelTemplateFiles.put("model.mustache", ".java");

        apiTemplateFiles.clear();
        apiTemplateFiles.put("api.mustache", ".java");

        embeddedTemplateDir = templateDir = "JavaJoobyTemplate";

        apiPackage = rootPackage + ".server";

        modelPackage = rootPackage + ".model";

        additionalProperties.put(ROOT_PACKAGE, rootPackage);

        groupId = "io.swagger";
        artifactId = "swagger-java-jooby-server";
        artifactVersion = apiVersion;

        this.setDateLibrary("java8");

    }

    public CodegenType getTag() {
        return CodegenType.SERVER;
    }

    public String getName() {
        return "java-jooby";
    }

    public String getHelp() {
        return "Generates a java-jooby Server library.";
    }

    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        super.preprocessOpenAPI(openAPI);
        try {
            URL value = new URL(openAPI.getServers().stream()
                    .map(Server::getUrl)
                    .findFirst()
                    .orElse("http://localhost:8080/"));

            int port = value.getPort();
            additionalProperties.put("serverPort", port == -1 ? "8080" : String.valueOf(port));
        } catch (MalformedURLException e) {
            additionalProperties.put("serverPort", 8080);

        }
    }

    @Override
    public void processOpenAPI(OpenAPI openAPI) {
        super.processOpenAPI(openAPI);
    }

    @Override
    public void processOpts() {
        super.processOpts();

        writeOptional(outputFolder, new SupportingFile(".gitignore", "", ".gitignore"));
        writeOptional(outputFolder, new SupportingFile("README.mustache", "", "README.md"));
        writeOptional(outputFolder, new SupportingFile("pom.mustache", "", "pom.xml"));
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        if (!model.getIsEnum()) {
            model.imports.add("SerializedName");
        }
        model.imports.remove("Schema");
    }

    @Override
    public Map<String, Object> postProcessModels(Map<String, Object> objs) {
        return super.postProcessModels(objs);

    }

    @Override
    public Map<String, Object> postProcessOperations(Map<String, Object> objs) {
        Map<String, Object> newObjs = super.postProcessOperations(objs);
        Map<String, Object> operations = (Map<String, Object>) newObjs.get("operations");
        if (operations != null) {
            List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");
            for (CodegenOperation operation : ops) {
                operation.httpMethod = operation.httpMethod.toLowerCase();

                if (operation.returnType == null) {
                    operation.returnType = "void";
                }

                if ("Void".equalsIgnoreCase(operation.returnType)) {
                    operation.returnType = "Object";
                }

                if (operation.defaultResponse == null
                        || operation.defaultResponse.equalsIgnoreCase("null")) {
                    operation.defaultResponse = operation.returnType;
                }

                if (operation.getHasPathParams()) {
                    operation.path = camelizePath(operation.path);
                }
            }
        }
        operations.putAll(additionalProperties);
        return newObjs;
    }

    private String camelizePath(String path) {
        String word = path;
        Pattern pattern = Pattern.compile("(_)(.)");
        Matcher matcher = pattern.matcher(word);
        while (matcher.find()) {
            word = matcher.replaceFirst(matcher.group(2).toUpperCase());
            matcher = pattern.matcher(word);
        }
        return word;
    }
}
