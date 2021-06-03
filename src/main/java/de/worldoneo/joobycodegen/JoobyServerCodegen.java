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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JoobyServerCodegen extends AbstractJavaCodegen {
    public static final String ROOT_PACKAGE = "rootPackage";
    private final Map<String, Object> modelEnums = new HashMap<>();
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
        super.preprocessOpenAPI(openAPI);
        openAPI.getComponents().getSchemas().forEach((s, schema) -> {
            List<?> anEnum = schema.getEnum();
            if (anEnum != null) {
                Object value = anEnum.get(0);
                if (value instanceof String)
                    value = "\"" + value + "\"";

                modelEnums.put(s, value);
            }
        });
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
            model.imports.add("JsonProperty");
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

                operation.vendorExtensions.put("x-is-venum", modelEnums.containsKey(operation.returnType));
                operation.vendorExtensions.put("x-return-enumDefault", modelEnums.get(operation.returnType));
                CodegenParameter bodyParam = operation.bodyParam;
                if(bodyParam != null) {
                    operation.vendorExtensions.put("x-req-renum", modelEnums.containsKey(bodyParam.baseType));
                    operation.vendorExtensions.put("x-req-renumDefault", modelEnums.get(bodyParam.baseType));
                    operation.vendorExtensions.put("x-req-bodyType", bodyParam.baseType);
                }

                operation.vendorExtensions.put("x-is-get", operation.getHttpMethod().equalsIgnoreCase("get"));

                for (CodegenParameter p : operation.allParams) {

                    String resolveKey = "x-ResolveMethod";
                    String setterKey = "x-MockingMethod";
                    String parseFunction = "x-ParseMethod";
                    String baseName = p.baseName;
                    p.vendorExtensions.put("x-param-Name", p.paramName);
                    String dataType = p.getDataType();
                    boolean required = p.required;

                    String toParse = required
                            ? "to(" + dataType + ".class)"
                            : "toOptional(" + dataType + ".class)";

                    Boolean isString = p.getIsString();
                    if (required) {
                        toParse = isString ? "value()" : toParse;
                        toParse = p.getIsInteger() ? "intValue()" : toParse;
                        toParse = p.getIsLong() ? "longValue()" : toParse;
                        toParse = p.getIsBoolean() ? "booleanValue()" : toParse;
                        toParse = p.getIsFloat() ? "floatValue()" : toParse;
                        toParse = p.getIsDouble() ? "doubleValue()" : toParse;
                    }

                    String testData = isString ? "\"test-data\"" : "\"0\"";
                    String put = ".put(\"" + baseName + "\", " + testData + ")";
                    p.vendorExtensions.put(parseFunction, toParse);

                    Consumer<String> inServer = s -> p.vendorExtensions.put(resolveKey, s);
                    Consumer<String> inTest = s -> p.vendorExtensions.put(setterKey, s);
                    if (p.getIsQueryParam()) {
                        inServer.accept("query(\"" + baseName + "\")");
                        inTest.accept("queryMap()" + put);
                    } else if (p.getIsPathParam()) {
                        inServer.accept("path(\"" + baseName + "\")");
                    } else if (p.getIsBodyParam()) {
                        inServer.accept("body()");
                    } else if (p.getIsCookieParam()) {
                        inServer.accept("cookie(\"" + baseName + "\")");
                        inTest.accept("cookieMap()" + put);
                    } else if (p.getIsFormParam()) {
                        inServer.accept("form(\"" + baseName + "\")");
                        inTest.accept("formData()" + put);
                    } else if (p.getIsHeaderParam()) {
                        inServer.accept("header(\"" + baseName + "\")");
                        inTest.accept("setRequestHeader(\"" + baseName + "\", " + testData + ")");
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
