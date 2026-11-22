package com.rmbreeding.api.contract;

import com.rmbreeding.api.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P17-02 - tests de contrat OpenAPI.
 *
 * <p>Deux contrôles complémentaires :</p>
 * <ul>
 *   <li>le document publié par l'application ({@code /v3/api-docs}) expose bien les métadonnées,
 *       le schéma de sécurité et les opérations critiques ;</li>
 *   <li>les contrats versionnés dans {@code docs/openapi/*.yaml} sont des documents OpenAPI 3
 *       valides (références résolues, réponses déclarées) et leurs parcours critiques existent
 *       réellement dans le document runtime.</li>
 * </ul>
 */
class OpenApiContractIT extends IntegrationTestBase {

    private static final List<String> CONTRACT_FILES = List.of(
            "openapi-auth.yaml",
            "openapi-referential.yaml",
            "openapi-domain.yaml",
            "openapi-commercial.yaml",
            "openapi-pilotage.yaml");

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "post", "put", "patch", "delete", "head", "options", "trace");

    /** Opérations critiques : chemin runtime -> méthode HTTP. */
    private static final Map<String, String> CRITICAL_RUNTIME_OPERATIONS = new LinkedHashMap<>();

    static {
        CRITICAL_RUNTIME_OPERATIONS.put("/api/v1/auth/register", "post");
        CRITICAL_RUNTIME_OPERATIONS.put("/api/v1/auth/login", "post");
        CRITICAL_RUNTIME_OPERATIONS.put("/api/v1/auth/me", "get");
        CRITICAL_RUNTIME_OPERATIONS.put("/api/v1/animals", "get");
        CRITICAL_RUNTIME_OPERATIONS.put("/api/v1/animals", "post");
        CRITICAL_RUNTIME_OPERATIONS.put("/api/v1/lots/{lotId}/close", "post");
        CRITICAL_RUNTIME_OPERATIONS.put("/api/v1/sales", "post");
        CRITICAL_RUNTIME_OPERATIONS.put("/api/v1/customers", "post");
        CRITICAL_RUNTIME_OPERATIONS.put("/api/v1/indicators", "get");
        CRITICAL_RUNTIME_OPERATIONS.put("/api/v1/sync/push", "post");
        CRITICAL_RUNTIME_OPERATIONS.put("/api/v1/audit", "get");
        CRITICAL_RUNTIME_OPERATIONS.put("/api/v1/tenants/{tenantId}/export", "get");
        CRITICAL_RUNTIME_OPERATIONS.put("/api/v1/export-jobs", "post");
    }

    /** Parcours critiques documentés dans les contrats versionnés, sans base de serveur. */
    private static final Map<String, Map<String, String>> CRITICAL_CONTRACT_OPERATIONS = Map.of(
            "openapi-auth.yaml", Map.of(
                    "/auth/register", "post",
                    "/auth/login", "post",
                    "/auth/me", "get",
                    "/tenants/{tenantId}/members", "get"),
            "openapi-referential.yaml", Map.of(
                    "/animals", "get",
                    "/lots", "post",
                    "/lots/{id}/close", "post",
                    "/species", "get"),
            "openapi-domain.yaml", Map.of(
                    "/reproduction/services", "post",
                    "/health/products", "get",
                    "/feeds", "post"),
            "openapi-commercial.yaml", Map.of(
                    "/sales", "post",
                    "/customers", "post",
                    "/expenses", "post",
                    "/revenues", "post"),
            "openapi-pilotage.yaml", Map.of(
                    "/indicators", "get",
                    "/dashboard", "get",
                    "/sync/push", "post",
                    "/audit", "get"));

    @Test
    @DisplayName("Le document OpenAPI runtime expose les métadonnées, la sécurité et les schémas attendus")
    void runtimeDocumentIsPublishedAndWellFormed() {
        Map<String, Object> spec = runtimeSpec();

        assertThat(spec.get("openapi")).as("version OpenAPI").isInstanceOf(String.class);
        assertThat((String) spec.get("openapi")).startsWith("3.");

        Map<String, Object> info = asMap(spec.get("info"), "info");
        assertThat(info.get("title")).isEqualTo("RM Breeding API");
        assertThat(info.get("version")).isEqualTo("v1");
        assertThat((String) info.get("description")).isNotBlank();

        Map<String, Object> components = asMap(spec.get("components"), "components");
        Map<String, Object> schemes = asMap(components.get("securitySchemes"), "securitySchemes");
        Map<String, Object> bearer = asMap(schemes.get("bearerAuth"), "bearerAuth");
        assertThat(bearer.get("type")).isEqualTo("http");
        assertThat(bearer.get("scheme")).isEqualTo("bearer");
        assertThat(bearer.get("bearerFormat")).isEqualTo("JWT");

        List<Object> topLevelSecurity = asList(spec.get("security"), "security");
        assertThat(topLevelSecurity).anySatisfy(requirement ->
                assertThat(asMap(requirement, "security requirement")).containsKey("bearerAuth"));

        Map<String, Object> schemas = asMap(components.get("schemas"), "schemas");
        assertThat(schemas).containsKeys(
                "RegisterRequest", "RegisterResponse", "LoginRequest", "TokenPair", "MeResponse",
                "AnimalCreateRequest", "AnimalResponse", "LotClosureResponse",
                "PushRequest", "PushResponse");
    }

    @Test
    @DisplayName("Chaque opération critique est documentée avec une réponse de succès et un identifiant unique")
    void criticalOperationsAreDocumented() {
        Map<String, Object> spec = runtimeSpec();
        Map<String, Object> paths = asMap(spec.get("paths"), "paths");
        assertThat(paths).isNotEmpty();

        List<String> operationIds = new ArrayList<>();
        CRITICAL_RUNTIME_OPERATIONS.forEach((path, method) -> {
            Map<String, Object> pathItem = asMap(paths.get(path), "path " + path);
            Map<String, Object> operation = asMap(pathItem.get(method), method + " " + path);

            Map<String, Object> responses = asMap(operation.get("responses"), "responses " + method + " " + path);
            assertThat(responses.keySet())
                    .as("au moins une réponse 2xx documentée pour %s %s", method, path)
                    .anyMatch(code -> code.startsWith("2"));

            Object operationId = operation.get("operationId");
            if (operationId != null) {
                operationIds.add((String) operationId);
            }
        });

        assertThat(operationIds).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Les contrats versionnés sont des documents OpenAPI 3 valides")
    void committedContractsAreValidOpenApiDocuments() {
        for (String file : CONTRACT_FILES) {
            Map<String, Object> contract = loadContract(file);

            assertThat(contract.get("openapi")).as("%s: version OpenAPI", file)
                    .isInstanceOf(String.class);
            assertThat((String) contract.get("openapi")).as("%s: version OpenAPI", file).startsWith("3.");

            Map<String, Object> info = asMap(contract.get("info"), file + ": info");
            assertThat((String) info.get("title")).as("%s: titre", file).isNotBlank();
            assertThat((String) info.get("version")).as("%s: version d'info", file).isNotBlank();

            Map<String, Object> paths = asMap(contract.get("paths"), file + ": paths");
            assertThat(paths).as("%s: paths", file).isNotEmpty();

            Map<String, Object> components = contract.get("components") instanceof Map<?, ?> c
                    ? castMap(c) : Map.of();
            Set<String> declaredSchemas = components.get("schemas") instanceof Map<?, ?> s
                    ? castMap(s).keySet() : Set.of();

            paths.forEach((path, pathValue) -> {
                assertThat(path).as("%s: chemin relatif", file).startsWith("/");
                Map<String, Object> pathItem = asMap(pathValue, file + ": " + path);
                List<String> methods = pathItem.keySet().stream()
                        .filter(HTTP_METHODS::contains)
                        .toList();
                assertThat(methods).as("%s: %s doit déclarer une opération", file, path).isNotEmpty();

                for (String method : methods) {
                    Map<String, Object> operation = asMap(pathItem.get(method),
                            file + ": " + method + " " + path);
                    Map<String, Object> responses = asMap(operation.get("responses"),
                            file + ": réponses " + method + " " + path);
                    assertThat(responses).as("%s: réponses de %s %s", file, method, path).isNotEmpty();
                }
            });

            List<String> refs = new ArrayList<>();
            collectRefs(contract, refs);
            for (String ref : refs) {
                if (ref.startsWith("#/components/schemas/")) {
                    String schema = ref.substring("#/components/schemas/".length());
                    assertThat(declaredSchemas).as("%s: référence %s résolue", file, ref)
                            .contains(schema);
                }
            }

            if (usesBearer(contract)) {
                Set<String> schemes = components.get("securitySchemes") instanceof Map<?, ?> s
                        ? castMap(s).keySet() : Set.of();
                assertThat(schemes).as("%s: schéma bearerAuth déclaré", file).contains("bearerAuth");
            }
        }
    }

    @Test
    @DisplayName("Les parcours critiques des contrats versionnés existent dans le document runtime")
    void criticalContractPathsExistAtRuntime() {
        Map<String, Object> runtime = runtimeSpec();
        Map<String, Object> runtimePaths = asMap(runtime.get("paths"), "paths");
        Set<String> normalizedRuntime = new HashSet<>();
        runtimePaths.keySet().forEach(path -> normalizedRuntime.add(normalizePath(path)));

        CRITICAL_CONTRACT_OPERATIONS.forEach((file, operations) -> {
            Map<String, Object> contract = loadContract(file);
            Map<String, Object> contractPaths = asMap(contract.get("paths"), file + ": paths");

            operations.forEach((contractPath, method) -> {
                String runtimePath = normalizePath("/api/v1" + contractPath);
                assertThat(normalizedRuntime)
                        .as("%s: %s %s doit exister côté runtime", file, method, contractPath)
                        .contains(runtimePath);
            });
        });
    }

    private Map<String, Object> runtimeSpec() {
        Map<?, ?> body = api().get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertThat(body).as("corps du document OpenAPI").isNotNull();
        return castMap(body);
    }

    private Map<String, Object> loadContract(String file) {
        Path base = Path.of("..", "docs", "openapi");
        if (!Files.isDirectory(base)) {
            base = Path.of("docs", "openapi");
        }
        Path path = base.resolve(file);
        assertThat(path).as("contrat %s présent", file).exists();

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Object loaded = new Yaml().load(reader);
            return asMap(loaded, file);
        } catch (IOException e) {
            throw new AssertionError("Lecture impossible du contrat " + file, e);
        }
    }

    /** Remplace les noms de variables de chemin : {@code /lots/{lotId}/close} -> {@code /lots/{}/close}. */
    private static String normalizePath(String path) {
        return path.replaceAll("\\{[^/]+}", "{}");
    }

    private static boolean usesBearer(Object node) {
        if (node instanceof Map<?, ?> map) {
            if (map.containsKey("bearerAuth")) {
                return true;
            }
            for (Object value : map.values()) {
                if (usesBearer(value)) {
                    return true;
                }
            }
        } else if (node instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (usesBearer(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void collectRefs(Object node, List<String> refs) {
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if ("$ref".equals(key) && value instanceof String ref) {
                    refs.add(ref);
                } else {
                    collectRefs(value, refs);
                }
            });
        } else if (node instanceof Collection<?> collection) {
            collection.forEach(item -> collectRefs(item, refs));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value, String label) {
        assertThat(value).as("%s doit être un objet", label).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value, String label) {
        assertThat(value).as("%s doit être une liste", label).isInstanceOf(List.class);
        return (List<Object>) value;
    }
}
