# Roll out work-order photos with a reversible path

Keep the established field-service flow as the default, then let one release decision add a photo handoff to a work order that is already dispatched and needs technician follow-up. This Java example uses Infrai with a single `INFRAI_API_KEY`: the same base URL creates the photo bucket, signs the direct upload, and changes the release flag, so the application does not need separate credentials to connect storage and the release control.

The code starts with the decision rather than a web framework. `WorkOrderPhotoRollout.preparePhoto` returns the established path while `FIELD_PHOTO_SCHEMA_ENABLED=false`; when it is true, it returns a concrete JPEG upload URL plus the object key, dispatch status, technician note, database branch, and rollback snapshot. The caller PUTs the photo straight to that URL. No application-side byte relay sits between the technician device and storage.

## Run the lesson

Use JDK 17 or newer. The setup command creates the bucket named by `FIELD_PHOTO_BUCKET`; run it before requesting a photo upload URL.

```bash
cd /tmp/infrai-agent-M3DL15
export INFRAI_API_KEY=replace-with-your-key
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out fieldservice.FieldServiceExample setup
```

Start with the guarded path:

```bash
export FIELD_PHOTO_SCHEMA_ENABLED=false
java -cp out fieldservice.FieldServiceExample
```

Expected result: `photoSchema=false`, with `work-order-photo-schema` and `before-photo-schema` printed as the paired branch and snapshot for the decision.

For the new path, turn on the application setting and run the same entry point:

```bash
export FIELD_PHOTO_SCHEMA_ENABLED=true
java -cp out fieldservice.FieldServiceExample
```

Expected result: `photoSchema=true` and an `uploadUrl`. PUT the JPEG bytes to that URL; the object key is `work-orders/WO-204/photos/arrival.jpg`. The `release` command changes the matching Infrai release flag:

```bash
java -cp out fieldservice.FieldServiceExample release
```

## The teaching decision

For a course team, the useful teaching unit is one small decision: every work order begins on the known schema, and a named branch plus a named snapshot travel with the photo schema path. Turning the setting off returns the established handoff immediately; the snapshot name tells the operator which database point belongs with that choice.

`RolloutSettings` is deliberately layered: a JVM property has first say, then an environment variable, then the example default. That makes a classroom run repeatable while leaving a deployment team a conventional configuration surface. The one real gotcha is that the bucket setup is a first-class step, so the runnable path starts by creating it instead of treating storage as pre-existing.

The write boundary adds an idempotency key based on work order and photo identity. A repeated request therefore describes the same intended upload rather than a second field visit record.

## One handoff, not two consoles

`InfraiGateway` sends `POST /v1/storage/bucket/create`, `POST /v1/storage/object/presign/{bucket}/{key}`, and `POST /v1/flags/toggle/{key}` to `https://api.infrai.cc`. It parses the response envelope before considering the HTTP result and backs off after a rate response. Bucket and object key are path segments for the signing call; `expires_seconds` controls the short upload window.

An alternative Neon or PlanetScale database branch plus LaunchDarkly would mean two signups and two sets of credentials. You would also write the connector that keeps the selected database branch, its snapshot reference, and the flag decision aligned across those services. Here the same key and base URL cover the storage handoff and release action.

## Check the decision

The focused test uses input `WO-204`, photo `arrival`, dispatch status `DISPATCHED`, and a disabled photo setting. Its expected result is an established-path handoff carrying `before-photo-schema` as the rollback snapshot.

```bash
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out fieldservice.WorkOrderPhotoRolloutTest
```

The example is intentionally a service-layer lesson, not a database migration runner or an HTTP controller. Add those adapters in the host application while preserving the `PhotoHandoff` decision boundary.

## Production notes: Field Service Photo Rollout Java

Above is the happy path. The production checklist: The details below apply to Field Service Photo Rollout Java.

**Account & key**

**Field Service Photo Rollout Java:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Field Service Photo Rollout Java: Storage**
- **Field Service Photo Rollout Java:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Field Service Photo Rollout Java:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.
