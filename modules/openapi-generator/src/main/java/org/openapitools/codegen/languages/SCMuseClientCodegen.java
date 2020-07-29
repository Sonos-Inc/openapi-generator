/*
 * Copyright 2018 OpenAPI-Generator Contributors (https://openapi-generator.tech)
 * Copyright 2018 SmartBear Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openapitools.codegen.languages;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.OpenAPI;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.*;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

import static org.openapitools.codegen.utils.StringUtils.*;

public class SCMuseClientCodegen extends AbstractCppCodegen {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(SCMuseClientCodegen.class);

    public static final String DECLSPEC = "declspec";
    public static final String DEFAULT_INCLUDE = "defaultInclude";

    protected String packageVersion = "1.0.0";
    protected String declspec = "";
    protected String defaultInclude = "";

    private Set<String> targets;

    public SCMuseClientCodegen() {
        super();

        apiPackage = "muse.api";
        modelPackage = "muse.model";

        modelTemplateFiles.put("Model.h.mustache", ".h");
        modelTemplateFiles.put("Model.cxx.mustache", ".cxx");

        apiTemplateFiles.put("Api.h.mustache", ".h");
        apiTemplateFiles.put("Api.cxx.mustache", ".cxx");

        embeddedTemplateDir = templateDir = "sc-muse-client";

        cliOptions.clear();

        // CLI options
        addOption(CodegenConstants.MODEL_PACKAGE, "C++ namespace for models (convention: name.space.model).",
                this.modelPackage);
        addOption(CodegenConstants.API_PACKAGE, "C++ namespace for apis (convention: name.space.api).",
                this.apiPackage);
        addOption(CodegenConstants.PACKAGE_VERSION, "C++ package version.", this.packageVersion);
        addOption(DECLSPEC, "C++ preprocessor to place before the class name for handling dllexport/dllimport.",
                this.declspec);
        addOption(DEFAULT_INCLUDE,
                "The default include statement that should be placed in all headers for including things like the declspec (convention: #include \"Commons.h\" ",
                this.defaultInclude);

        languageSpecificPrimitives = new HashSet<String>(
                Arrays.asList("int", "char", "bool", "long", "float", "double", "int32_t", "int64_t"));

        typeMapping = new HashMap<String, String>();
        typeMapping.put("date", "std::string");
        typeMapping.put("DateTime", "std::string");
        typeMapping.put("string", "std::string");
        typeMapping.put("integer", "int32_t");
        typeMapping.put("long", "int64_t");
        typeMapping.put("boolean", "bool");
        typeMapping.put("array", "std::vector");
        typeMapping.put("map", "std::map");
        typeMapping.put("file", "std::string");
        typeMapping.put("object", "Object");
        typeMapping.put("binary", "std::string");
        typeMapping.put("number", "double");
        typeMapping.put("UUID", "std::string");
        typeMapping.put("URI", "std::string");
        typeMapping.put("ByteArray", "std::string");

        super.importMapping = new HashMap<String, String>();
        importMapping.put("std::vector", "#include <vector>");
        importMapping.put("std::map", "#include <map>");
        importMapping.put("std::string", "#include <string>");
        importMapping.put("Object", "#include \"MuseObject.h\"");

        targets = new HashSet<String>();
        targets.add("households");
        targets.add("players");
        targets.add("groups");
        targets.add("sessions");
    }

    /**
     * Configures the type of generator.
     *
     * @return the CodegenType for this generator
     */
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    /**
     * Configures a friendly name for the generator. This will be used by the
     * generator to select the library with the -g flag.
     *
     * @return the friendly name for the generator
     */
    public String getName() {
        return "sc-muse-client";
    }

    /**
     * Returns human-friendly help for the generator. Provide the consumer with help
     * tips, parameters here
     *
     * @return A string value for the help message
     */
    public String getHelp() {
        return "Generates a C++ API Client for Muse.";
    }

    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        super.preprocessOpenAPI(openAPI);
        String majorVersion = "1";
        String version = openAPI.getInfo().getVersion();
        String[] split = version.split("\\.");
        if (split.length > 0) {
            // The split[0] has the 'v' character, substring(1) skips over it
            majorVersion = split[0].substring(1);
        }
        additionalProperties.put("x-muse-version", majorVersion);
    }

    @Override
    public void processOpts() {
        super.processOpts();

        if (additionalProperties.containsKey(DECLSPEC)) {
            declspec = additionalProperties.get(DECLSPEC).toString();
        }

        if (additionalProperties.containsKey(DEFAULT_INCLUDE)) {
            defaultInclude = additionalProperties.get(DEFAULT_INCLUDE).toString();
        }

        additionalProperties.put("modelNamespaceDeclarations", modelPackage.split("\\."));
        additionalProperties.put("modelNamespace", modelPackage.replaceAll("\\.", "::"));
        additionalProperties.put("apiNamespaceDeclarations", apiPackage.split("\\."));
        additionalProperties.put("apiNamespace", apiPackage.replaceAll("\\.", "::"));
        additionalProperties.put("declspec", declspec);
        additionalProperties.put("defaultInclude", defaultInclude);
    }

    /**
     * Location to write model files. You can use the modelPackage() as defined when
     * the class is instantiated
     */
    public String modelFileFolder() {
        return (outputFolder + "/model").replace("/", File.separator);
    }

    /**
     * Location to write api files. You can use the apiPackage() as defined when the
     * class is instantiated
     */
    @Override
    public String apiFileFolder() {
        return (outputFolder + "/api").replace("/", File.separator);
    }

    @Override
    public String toModelImport(String name, boolean isApi) {
        if (importMapping.containsKey(name)) {
            return importMapping.get(name);
        } else if (name.startsWith("boost::variant<")) {
            String imports = "#include \"boost/variant.hpp\"\n";

            int length = name.length();
            String[] split = name.substring(15, length - 1).split(",");
            for (String inner : split) {
                imports = imports + toModelImport(inner, isApi) + "\n";
            }

            return imports;
        } else {
            if (isApi) {
                return "#include \"model/" + name + ".h\"";
            } else {
                return "#include \"" + name + ".h\"";
            }
        }
    }

    @Override
    public CodegenModel fromModel(String name, Schema model) {
        CodegenModel codegenModel = super.fromModel(name, model);
        Set<String> oldImports = codegenModel.imports;
        codegenModel.imports = new HashSet<String>();
        for (String imp : oldImports) {
            String newImp = toModelImport(imp, false);
            if (!newImp.isEmpty()) {
                codegenModel.imports.add(newImp);
            }
        }

        return codegenModel;
    }

    @Override
    public String toModelFilename(String name) {
        return toModelName(name);
    }

    @Override
    public String toApiFilename(String name) {
        return toApiName(name);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> postProcessOperationsWithModels(Map<String, Object> objs, List<Object> allModels) {
        Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        List<CodegenOperation> opList = (List<CodegenOperation>) operations.get("operation");

        HashSet<String> typesInserted = new HashSet<String>();
        opList.forEach(op -> {
            op.vendorExtensions.put("x-muse-target", getTargetFromPath(op.path));
            op.vendorExtensions.put("x-muse-namespace", getNamespace(op.baseName));
            op.vendorExtensions.put("x-muse-commandName", getCommandName(op.operationIdOriginal));
            op.vendorExtensions.put("x-muse-enum-prefix", op.baseName.toUpperCase(Locale.ROOT));

            op.responses.forEach(response -> {
                if (response != null && response.dataType != null) {
                    if (!typesInserted.contains(response.dataType)) {
                        if (response.dataType.contains("boost::variant")) {
                            // '15' is length of "boost::variant<"
                            // split off the ending ">" as well.
                            String internalTypesStr = response.dataType.substring(15, response.dataType.length() - 1);
                            String[] internalTypesArr = internalTypesStr.split(",");
                            for (String dataType: internalTypesArr) {
                                if (!typesInserted.contains(dataType)) {
                                    response.vendorExtensions.put("x-muse-enum-entry", dataType.toUpperCase(Locale.ROOT));
                                    response.vendorExtensions.put("x-muse-responseType", toCamelCase(dataType));
                                    typesInserted.add(dataType);
                                }
                            }
                        } else {
                            response.vendorExtensions.put("x-muse-enum-entry", response.dataType.toUpperCase(Locale.ROOT));
                            response.vendorExtensions.put("x-muse-responseType", toCamelCase(response.dataType));
                        }
                        typesInserted.add(response.dataType);
                    }
                }
            });
        });

        return objs;
    }

    public String getTargetFromPath(String path) {
        String[] split = path.split("/");

        if (split.length >= 2 && targets.contains(split[1])) {
            return split[1];
        }

        return "none";
    }

    String toCamelCase(String s) {
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    public String getNamespace(String baseName) {
        return toCamelCase(baseName);
    }

    public String getCommandName(String operationId) {
        String[] split = operationId.split("-");
        if (split.length >= 2) {
            return toCamelCase(split[1]);
        }
        return operationId;
    }

    /**
     * Optional - type declaration. This is a String which is used by the templates
     * to instantiate your types. There is typically special handling for different
     * property types
     *
     * @return a string value used as the `dataType` field for model templates,
     *         `returnType` for api templates
     */
    @Override
    public String getTypeDeclaration(Schema p) {
        String openAPIType = getSchemaType(p);

        if (ModelUtils.isArraySchema(p)) {
            ArraySchema ap = (ArraySchema) p;
            Schema inner = ap.getItems();
            return getSchemaType(p) + "<" + getTypeDeclaration(inner) + ">";
        } else if (ModelUtils.isMapSchema(p)) {
            Schema inner = ModelUtils.getAdditionalProperties(p);
            return getSchemaType(p) + "<std::string, " + getTypeDeclaration(inner) + ">";
        } else if (ModelUtils.isByteArraySchema(p)) {
            return "std::string";
        } else if (ModelUtils.isStringSchema(p) || ModelUtils.isDateSchema(p) || ModelUtils.isDateTimeSchema(p)
                || ModelUtils.isFileSchema(p) || languageSpecificPrimitives.contains(openAPIType)) {
            return toModelName(openAPIType);
        }

        return openAPIType;
    }

    @Override
    public String getTypeDeclaration(String str) {
        return toModelName(str);
    }

    @Override
    public String toDefaultValue(Schema p) {
        if (ModelUtils.isStringSchema(p)) {
            if (p.getDefault() != null) {
                return "\"" + p.getDefault().toString() + "\"";
            } else {
                return "\"\"";
            }
        } else if (ModelUtils.isBooleanSchema(p)) {
            if (p.getDefault() != null) {
                return p.getDefault().toString();
            } else {
                return "false";
            }
        } else if (ModelUtils.isDateSchema(p)) {
            if (p.getDefault() != null) {
                return "\"" + p.getDefault().toString() + "\"";
            } else {
                return "\"\"";
            }
        } else if (ModelUtils.isDateTimeSchema(p)) {
            if (p.getDefault() != null) {
                return "\"" + p.getDefault().toString() + "\"";
            } else {
                return "\"\"";
            }
        } else if (ModelUtils.isNumberSchema(p)) {
            if (ModelUtils.isFloatSchema(p)) { // float
                if (p.getDefault() != null) {
                    return p.getDefault().toString() + "f";
                } else {
                    return "0.0f";
                }
            } else { // double
                if (p.getDefault() != null) {
                    return p.getDefault().toString();
                } else {
                    return "0.0";
                }
            }
        } else if (ModelUtils.isIntegerSchema(p)) {
            if (ModelUtils.isLongSchema(p)) { // long
                if (p.getDefault() != null) {
                    return p.getDefault().toString() + "L";
                } else {
                    return "0L";
                }
            } else { // integer
                if (p.getDefault() != null) {
                    return p.getDefault().toString();
                } else {
                    return "0";
                }
            }
        } else if (ModelUtils.isByteArraySchema(p)) {
            if (p.getDefault() != null) {
                return "\"" + p.getDefault().toString() + "\"";
            } else {
                return "\"\"";
            }
        } else if (ModelUtils.isMapSchema(p)) {
            String inner = getSchemaType(ModelUtils.getAdditionalProperties(p));
            return "std::map<std::string, " + inner + ">()";
        } else if (ModelUtils.isArraySchema(p)) {
            ArraySchema ap = (ArraySchema) p;
            String inner = getSchemaType(ap.getItems());
            return "std::vector<" + inner + ">()";
        } else if (!StringUtils.isEmpty(p.get$ref())) {
            return toModelName(ModelUtils.getSimpleRef(p.get$ref())) + "()";
        }

        return "nullptr";
    }

    @Override
    public void postProcessParameter(CodegenParameter parameter) {
        super.postProcessParameter(parameter);

        boolean isPrimitiveType = parameter.isPrimitiveType == Boolean.TRUE;
        boolean isListContainer = parameter.isListContainer == Boolean.TRUE;
        boolean isString = parameter.isString == Boolean.TRUE;

        if (!isPrimitiveType && !isListContainer && !isString && !parameter.dataType.startsWith("std::shared_ptr")) {
            parameter.dataType = "const " + parameter.dataType + "&";
        }

        if (parameter.isBodyParam) {
            String[] split = parameter.baseName.split("_");
            if (split.length >= 2) {
                parameter.paramName = toCamelCase(split[1]);
            }
        }
    }

    /**
     * Optional - OpenAPI type conversion. This is used to map OpenAPI types in a
     * `Schema` into either language specific types via `typeMapping` or into
     * complex models if there is not a mapping.
     *
     * @return a string value of the type or complex model for this property
     */
    @Override
    public String getSchemaType(Schema p) {
        String openAPIType = super.getSchemaType(p);
        String type = null;
        if (typeMapping.containsKey(openAPIType)) {
            type = typeMapping.get(openAPIType);
        } else if (openAPIType.startsWith("oneOf<")) {
            type = "boost::variant<";
            int length = openAPIType.length();
            String[] split = openAPIType.substring(6, length - 1).split(",");
            for (String inner : split) {
                type = type + toModelName(inner) + ",";
            }
            type = type.substring(0, type.length() - 1);
            type = type + ">";
            return type;
        } else {
            type = openAPIType;
        }
        return toModelName(type);
    }
}
