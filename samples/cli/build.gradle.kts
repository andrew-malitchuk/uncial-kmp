plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

// A JVM harness for the SDK: the one place OCR can be run end to end on a developer
// machine or in CI without an emulator or a device. Tesseract behaves the same here as on
// Android, which is what makes this the practical home for the golden corpus. (PLAN.md §4)
//
// During phases 0-2 this consumes project(...) directly. From phase 6 the samples must
// consume the PUBLISHED artifacts via mavenLocal(), or they are not testing what ships.
dependencies {
    implementation(projects.sdk.runtime)
    implementation(projects.sdk.structure)
    // Engines are chosen by the consumer, not by the runtime.
    implementation(projects.sdk.engineTesseract)
    // The digital fast path is optional; this sample opts in.
    implementation(projects.sdk.pdfText)
    // Language data, so the sample works without a system Tesseract install.
    // The runtime downloader, exercised by the `download` command.
    implementation(projects.sdk.langDownload)
    runtimeOnly(projects.sdk.langUkr)
    runtimeOnly(projects.sdk.langEng)
    // Used only by the fixture generator below.
    implementation(libs.pdfbox)

    // The fixtures are the harness's one piece of real logic, and the claim they make --
    // "the scanned one has no text layer" -- fails silently if it ever stops being true.
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
}

application {
    mainClass = "io.github.andrewmalitchuk.uncial.samples.cli.source.entrypoint.MainKt"
}

tasks.withType<JavaExec>().configureEach {
    // The fixture generator draws through java.awt, which would otherwise try to reach a
    // display server: harmless on a developer machine, a crash on a headless CI runner.
    systemProperty("java.awt.headless", "true")
}
