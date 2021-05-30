package de.worldoneo.joobycodegen;

import io.swagger.codegen.v3.CodegenModel;
import io.swagger.codegen.v3.CodegenOperation;
import io.swagger.codegen.v3.CodegenParameter;
import io.swagger.codegen.v3.CodegenProperty;
import io.swagger.codegen.v3.CodegenType;
import io.swagger.codegen.v3.SupportingFile;
import io.swagger.codegen.v3.generators.java.AbstractJavaCodegen;
import io.swagger.codegen.v3.templates.MustacheTemplateEngine;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.servers.Server;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JoobyServerCodegen extends AbstractJavaCodegen {
    public static final String ROOT_PACKAGE = "rootPackage";
    protected String resourceFolder = "src/main/resources";
    protected String sourceFolder = "src/main/java";
    protected String rootPackage = System.getenv().getOrDefault("JOOBY_SERVER_PACKAGE", "org.simpleserver");
    protected String apiVersion = "1.0.0-SNAPSHOT";

    public JoobyServerCodegen() {
        super();

        // set the output folder here
        outputFolder = "generated-code" + File.separator + "javaJoobyServer";

        modelTemplateFiles.clear();
        modelTemplateFiles.put("model.mustache", ".java");

        embeddedTemplateDir = templateDir = "JavaJoobyTemplate";

        apiPackage = rootPackage + ".server";

        modelPackage = rootPackage + ".model";

        additionalProperties.put(ROOT_PACKAGE, rootPackage);

        groupId = "io.swagger";
        artifactId = "swagger-java-jooby-server";
        artifactVersion = apiVersion;

        this.setDateLibrary("java8");

    }

    private static String firstLetterLower(String camel) {
        byte[] name = camel.getBytes(StandardCharsets.UTF_8);
        if (name[0] > 64 && name[0] < 91)
            name[0] += 32;
        return new String(name);
    }

    @Override
    protected void setTemplateEngine() {
        this.templateEngine = new MustacheTemplateEngine(this);
    }

    public CodegenType getTag() {
        return CodegenType.SERVER;
    }

    public String getName() {
        return "java-jooby";
    }

    public String getHelp() {
        return "Generates a java-jooby Server.";
    }

    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        openAPI.getInfo().getTitle();
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

        String apiFolder = sourceFolder + '/' + apiPackage.replace('.', '/');
        writeOptional(outputFolder, new SupportingFile("endpoint.mustache", apiFolder, "Endpoint.java"));
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
    public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
        Map<String, Object> apiInfo = (Map<String, Object>) objs.get("apiInfo");
        List<Map<String, Object>> apis = (List<Map<String, Object>>) apiInfo.get("apis");
        for (Map<String, Object> api : apis) {
            api.put("lowerClassname", firstLetterLower((String) api.get("classname")));
        }
        return super.postProcessSupportingFileData(objs);
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

                for (CodegenParameter p : operation.allParams) {
                    String k = "x-param-ResolveMethod";
                    String paramName = p.getParamName();
                    String dataType = p.getDataType();
                    if (p.getIsQueryParam()) {
                        p.vendorExtensions.put(k, "query(\"" + paramName + "\").to(" + dataType + ".class)");
                    } else if (p.getIsPathParam()) {
                        p.vendorExtensions.put(k, "path(\"" + paramName + "\").to(" + dataType + ".class)");
                    } else if (p.getIsBodyParam()) {
                        p.vendorExtensions.put(k, "body(" + dataType + ".class)");
                    } else if (p.getIsCookieParam()) {
                        p.vendorExtensions.put(k, "cookie(\"" + paramName + ".class\").to(" + dataType + ".class)");
                    }
                }
            }
        }
        operations.putAll(additionalProperties);
        return newObjs;
    }

    @Override
    public void addOperationToGroup(String tag, String resourcePath, Operation operation, CodegenOperation co, Map<String, List<CodegenOperation>> operations) {
        String basePath = resourcePath;
        if (basePath.startsWith("/")) {
            basePath = basePath.substring(1);
        }
        int pos = basePath.indexOf("/");
        if (pos > 0) {
            basePath = basePath.substring(0, pos);
        }

        if (basePath.equals("")) {
            basePath = "default";
        }
        List<CodegenOperation> opList = operations.computeIfAbsent(basePath, k -> new ArrayList<>());
        if (opList.stream()
                .noneMatch(op -> co.getPath().equals(op.getPath())
                        && co.getHttpMethod().equals(op.getHttpMethod()))) {
            opList.add(co);
        }
    }

    @Override
    public String getDefaultTemplateDir() {
        return "JavaJoobyTemplate";
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

    @Override
    public String toApiName(String name) {
        if (name.length() == 0) {
            return "DefaultController";
        }
        name = name.replaceAll("[^a-zA-Z0-9]+", "_");
        return camelize(name) + "Controller";
    }
}
