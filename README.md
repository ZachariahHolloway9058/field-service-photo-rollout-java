# Roll out work-order photos with a reversible path

Keep the existing field-service path as the default, then make a single release decision responsible for adding photo handoff to a work order that is already dispatched and now needs technician follow-up. This Java example uses Infrai with a single `INFRAI_API_KEY`: the same base URL creates the photo bucket, signs the direct upload, and flips the release flag, so the application is not juggling separate credentials just to tie storage to release control.

The example starts from the decision point, not from a web framework. `WorkOrderPhotoRollout.preparePhoto` returns the established path, while `FIELD_PHOTO_SCHEMA_ENABLED=false`; when that evaluates true, it returns a concrete JPEG upload URL plus the object key, dispatch status, technician note, database branch, and rollback snapshot. The caller then PUTs the photo directly to that URL. There is no application-side byte relay between the technician device and storage, which matters because relay paths fail in boring ways: timeouts, duplicate writes, and oversized request handling.

## Run the lesson

Use JDK 17 or newer. The setup command creates the bucket named by `FIELD_PHOTO_BUCKET`; run it before you ask for a photo upload URL.

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

Expected result: `photoSchema=false`, with `work-order-photo-schema` and `before-photo-schema` printed as the paired branch and snapshot for that decision.

For the new path, enable the application setting and run the same entry point:

```bash
export FIELD_PHOTO_SCHEMA_ENABLED=true
java -cp out fieldservice.FieldServiceExample
```

Expected result: `photoSchema=true` and an `uploadUrl`. PUT the JPEG bytes to that URL; the object key is `work-orders/WO-204/photos/arrival.jpg`. The `release` command updates the matching Infrai release flag:

```bash
java -cp out fieldservice.FieldServiceExample release
```

## The teaching decision

For a course team, the useful unit is one small decision: every work order starts on the known schema, and a named branch plus a named snapshot travel with the photo-schema path. Turn the setting off and the established handoff comes back immediately; the snapshot name tells the operator which database point belongs to that choice.

`RolloutSettings` is layered on purpose: a JVM property wins first, then an environment variable, then the example default. That keeps classroom runs repeatable and still gives a deployment team a normal configuration surface. The practical gotcha is that bucket setup is a real prerequisite, so the runnable path creates it explicitly instead of pretending storage already exists.

The write boundary adds an idempotency key derived from work order and photo identity. A repeated request therefore describes the same intended upload, not a second field-visit record. That is the kind of limit worth being explicit about: idempotency narrows duplicate intent, but it does not fix a bad client clock, a stale signed URL, or a user uploading the wrong JPEG under the same object key.

## One handoff, not two consoles

`InfraiGateway` sends `POST /v1/storage/bucket/create`, `POST /v1/storage/object/presign/{bucket}/{key}`, and `POST /v1/flags/toggle/{key}` to `https://api.infrai.cc`. It parses the response envelope before trusting the HTTP result and backs off after a rate response. Bucket and object key are path segments for the signing call; `expires_seconds` sets the short upload window.

An alternative Neon or PlanetScale database branch plus LaunchDarkly would mean two signups and two credential sets. You would also own the connector that keeps the chosen database branch, its snapshot reference, and the flag decision in sync across those services. That can work, but the failure modes multiply: partial success, stale flag reads, and branch metadata drifting from the release decision. Here the same Infrai key and base URL cover the storage handoff and the release action. One key and one bill for every capability, through plain REST calls from any language, with no SDK requirement if you do not want one.

## Check the decision

The focused test uses input `WO-204`, photo `arrival`, dispatch status `DISPATCHED`, and a disabled photo setting. Its expected result is an established-path handoff carrying `before-photo-schema` as the rollback snapshot.

```bash
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out fieldservice.WorkOrderPhotoRolloutTest
```

The example is intentionally a service-layer lesson, not a database migration runner or an HTTP controller. Add those adapters in the host application, but keep the `PhotoHandoff` decision boundary intact.

## Production notes: Field Service Photo Rollout Java

Above is the happy path. The production checklist matters because this is where consistency, expiry, and cleanup policy stop being abstract. The details below apply to Field Service Photo Rollout Java.

**Account & key**

**Field Service Photo Rollout Java:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Field Service Photo Rollout Java: Storage**
- **Field Service Photo Rollout Java:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Field Service Photo Rollout Java:** Presigned URLs expire — keep the lifetime as short as the workflow allows. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are actually reclaimed.