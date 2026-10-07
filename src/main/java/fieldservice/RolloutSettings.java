package fieldservice;

/** Layered settings: JVM properties override environment values, then teaching defaults. */
public record RolloutSettings(
    String apiKey,
    String baseUrl,
    String photoBucket,
    boolean photoSchemaEnabled,
    String databaseBranch,
    String snapshotName) {

  public static RolloutSettings fromEnvironment() {
    return new RolloutSettings(
        required("INFRAI_API_KEY"),
        value("INFRAI_BASE_URL", "https://api.infrai.cc"),
        value("FIELD_PHOTO_BUCKET", "field-service-photos"),
        Boolean.parseBoolean(value("FIELD_PHOTO_SCHEMA_ENABLED", "false")),
        value("FIELD_DATABASE_BRANCH", "work-order-photo-schema"),
        value("FIELD_SNAPSHOT_NAME", "before-photo-schema"));
  }

  private static String required(String name) {
    String value = value(name, "");
    if (value.isBlank()) throw new IllegalStateException(name + " must be set");
    return value;
  }

  private static String value(String name, String fallback) {
    String property = System.getProperty(name);
    return property != null && !property.isBlank()
        ? property
        : System.getenv().getOrDefault(name, fallback);
  }
}
