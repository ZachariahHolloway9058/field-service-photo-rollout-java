package fieldservice;

import java.io.IOException;

/** Spring-style application service: its public method is the controller boundary. */
public final class WorkOrderPhotoRollout {
  private final RolloutSettings settings;
  private final InfraiGateway infrai;

  public WorkOrderPhotoRollout(RolloutSettings settings, InfraiGateway infrai) {
    this.settings = settings;
    this.infrai = infrai;
  }

  public PhotoHandoff preparePhoto(WorkOrderPhoto input) throws IOException, InterruptedException {
    if (!settings.photoSchemaEnabled()) {
      return PhotoHandoff.legacy(input.workOrderId(), settings.databaseBranch(), settings.snapshotName());
    }
    String key = "work-orders/" + input.workOrderId() + "/photos/" + input.photoId() + ".jpg";
    String uploadUrl = infrai.presignPhotoPut(settings.photoBucket(), key, input.workOrderId() + ":" + input.photoId());
    return PhotoHandoff.photo(input.workOrderId(), key, uploadUrl, input.dispatchStatus(), input.followUpNote(),
        settings.databaseBranch(), settings.snapshotName());
  }

  public void setPhotoSchemaRelease() throws IOException, InterruptedException {
    infrai.togglePhotoSchema("work-order-photo-schema");
  }

  public void prepareStorage() throws IOException, InterruptedException {
    infrai.createPhotoBucket(settings.photoBucket());
  }

  public record WorkOrderPhoto(String workOrderId, String photoId, String dispatchStatus, String followUpNote) { }

  public record PhotoHandoff(String workOrderId, boolean usesPhotoSchema, String objectKey, String uploadUrl,
      String dispatchStatus, String followUpNote, String databaseBranch, String rollbackSnapshot) {
    static PhotoHandoff legacy(String workOrderId, String branch, String snapshot) {
      return new PhotoHandoff(workOrderId, false, "", "", "DISPATCHED", "", branch, snapshot);
    }

    static PhotoHandoff photo(String workOrderId, String key, String uploadUrl, String status, String note,
        String branch, String snapshot) {
      return new PhotoHandoff(workOrderId, true, key, uploadUrl, status, note, branch, snapshot);
    }
  }
}
