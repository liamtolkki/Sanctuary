package dev.liamtolkkinen.sanctuary.persistence;

public record DatabaseMigration(
    int version,
    String name,
    String resourcePath
) {
}
