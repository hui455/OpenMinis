# Android Codex OAuth image calling model

## Observed failure

The image body builder hardcoded `gpt-5.5` as the top-level Responses model.
The request summary logged the selected image model instead of the serialized
model, hiding the distinction when the backend rejected `gpt-5.5` with HTTP 400.
The image model belongs in the `image_generation` tool; replacing the top-level
model with `gpt-image-2.5-sunburst` is not a valid fix.

## Change and use

In the Android provider settings, open the selected Codex image model's detail
screen. Set **Image calling model / 生图调用模型** to a Codex text model your
account can use and that supports the image generation tool, then save and
retry image generation. The image tool still names sunburst/flare as selected.
An empty field retains the upstream `gpt-5.5` default for existing accounts.
This patch deliberately does not guess which replacement is supported.

The setting is per image-model entry, survives config serialization and provider
export/import, and is passed through the existing provider factory. Invalid
image-model IDs or whitespace-containing IDs are rejected before sending.
Request summaries now log the actual top-level `model` and `selectedModel`.
No prompts or credential values were added to logs. API-key image generation
and iOS are unchanged. There is no automatic retry or silent model switching.

## Verification and remaining limits

Added regression cases to `CodexImageModelTest` for custom calling models,
blank/default behavior, invalid IDs, serialization, and unchanged image-tool
selection. Existing envelope tests continue to specify the legacy default.

`git diff --check` and XML parsing passed. The Gradle test command was attempted:

```sh
cd src/android
./gradlew :app:testDebugUnitTest --tests com.openminis.app.provider.CodexImageModelTest
```

It could not start because `services.gradle.org` failed DNS resolution while
downloading Gradle 8.11.1. Tests and compilation have NOT been verified, and
no APK has been built. No live ChatGPT OAuth image request was performed.
A model appearing in discovery or passing a text test alone does not establish
image-tool compatibility. This is a configuration/diagnostic repair, not proof
that the reported account can now generate images. A compatible calling model
and a successful real image request are still required for end-to-end closure.
