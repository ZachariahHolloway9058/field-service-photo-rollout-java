package fieldservice;

/** Explanatory entry point: run setup, release, or a photo handoff. */
public final class FieldServiceExample {
  public static void main(String[] args) throws Exception {
    RolloutSettings settings = RolloutSettings.fromEnvironment();
    WorkOrderPhotoRollout rollout = new WorkOrderPhotoRollout(settings, new InfraiGateway(settings));
    String command = args.length == 0 ? "handoff" : args[0];
    if ("setup".equals(command)) {
      rollout.prepareStorage();
      System.out.println("Photo bucket is ready.");
      return;
    }
    if ("release".equals(command)) {
      rollout.setPhotoSchemaRelease();
      System.out.println("Photo-schema release flag changed.");
      return;
    }
    WorkOrderPhotoRollout.PhotoHandoff handoff = rollout.preparePhoto(
        new WorkOrderPhotoRollout.WorkOrderPhoto("WO-204", "arrival", "DISPATCHED", "Review roof access"));
    System.out.println("workOrder=" + handoff.workOrderId());
    System.out.println("photoSchema=" + handoff.usesPhotoSchema());
    System.out.println("branch=" + handoff.databaseBranch() + ", snapshot=" + handoff.rollbackSnapshot());
    System.out.println("objectKey=" + handoff.objectKey());
    System.out.println("uploadUrl=" + handoff.uploadUrl());
  }
}
