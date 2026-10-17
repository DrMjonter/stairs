# Project Instructions

## Platform

This is a Minecraft mod targeting:

- Minecraft 1.21.1
- NeoForge
- Java 21
- ModDevGradle

Do not provide Forge, Fabric, Bukkit, Spigot, or Paper implementations unless explicitly requested.

Do not use APIs introduced after Minecraft 1.21.1.

## Build and Testing

Use the project's Gradle wrapper.

Windows commands:

./gradlew.bat build
./gradlew.bat runClient
./gradlew.bat runServer

After making meaningful Java or resource changes, run:

./gradlew.bat build

Do not report a change as complete if the project does not compile.

## Project Structure

Java source:

src/main/java

Resources:

src/main/resources

Generated or runtime files should not be manually committed unless intentionally part of the project.

## Development Style

Prefer simple, maintainable implementations over unnecessary abstraction.

Reuse existing project architecture and naming conventions before introducing new systems.

Before creating a new class or system, search the repository for an existing implementation that can be extended.

Avoid replacing large working systems when a localized modification is sufficient.

## Minecraft Development

Respect logical client/server separation.

Do not reference client-only Minecraft classes from code that may execute on a dedicated server.

Use NeoForge events and registration systems appropriate for Minecraft 1.21.1.

When implementing networking, validate that serverbound data cannot be trusted blindly from the client.

## Dependencies

Before adding a new dependency, check build.gradle and gradle.properties for existing dependencies.

Do not silently upgrade Minecraft, NeoForge, Gradle, Java, or other dependencies.

## AI Workflow

Before modifying a system:

1. Inspect the relevant existing classes.
2. Trace referenced classes when necessary.
3. Explain the likely cause of the issue.
4. Make the smallest appropriate change.
5. Build the project.
6. Report any remaining warnings or errors.

When debugging, prefer identifying the root cause rather than applying speculative changes.