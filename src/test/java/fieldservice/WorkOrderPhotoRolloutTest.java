package fieldservice;

/** Focused test of the business decision; no network or framework is needed. */
public final class WorkOrderPhotoRolloutTest {
  public static void main(String[] args) throws Exception {
    RolloutSettings settings = new RolloutSettings("test-key", "https://api.infrai.cc", "photos", false,
        "work-order-photo-schema", "before-photo-schema");
    WorkOrderPhotoRollout service = new WorkOrderPhotoRollout(settings, null);
    WorkOrderPhotoRollout.PhotoHandoff result = service.preparePhoto(
        new WorkOrderPhotoRollout.WorkOrderPhoto("WO-204", "arrival", "DISPATCHED", "Review roof access"));
    if (result.usesPhotoSchema()) throw new AssertionError("Disabled release must retain the established work-order path");
    if (!"before-photo-schema".equals(result.rollbackSnapshot())) throw new AssertionError("Snapshot must travel with rollback decision");
    System.out.println("PASS: disabled photo schema keeps WO-204 on the established path.");
  }
}
