package com.spring.teambondbackend.recommendation.utils;

import lombok.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

public class Mappings {
    // Helper class to store dependency-framework mapping
    @Getter @Setter @ToString @AllArgsConstructor @NoArgsConstructor
    public static class DependencyFramework { // dependency to framework mapping ( react -> React )
        String dependency;
        String framework;
        BiPredicate<String, String> checker;
    }

    public static final Map<String, List<String>> LANGUAGE_TO_CONFIG = new HashMap<>();
    static {
        LANGUAGE_TO_CONFIG.put("Java", Arrays.asList("pom.xml", "build.gradle"));
        LANGUAGE_TO_CONFIG.put("JavaScript", Arrays.asList("package.json"));
        LANGUAGE_TO_CONFIG.put("TypeScript", Arrays.asList("package.json"));
        LANGUAGE_TO_CONFIG.put("Python", Arrays.asList("requirements.txt", "pyproject.toml"));
        LANGUAGE_TO_CONFIG.put("PHP", Arrays.asList("composer.json"));
        LANGUAGE_TO_CONFIG.put("Ruby", Arrays.asList("Gemfile"));
        LANGUAGE_TO_CONFIG.put("Go", Arrays.asList("go.mod"));
        LANGUAGE_TO_CONFIG.put("Rust", Arrays.asList("Cargo.toml"));
        LANGUAGE_TO_CONFIG.put("Swift", Arrays.asList("Package.swift"));
        LANGUAGE_TO_CONFIG.put("Dart", Arrays.asList("pubspec.yaml"));
        LANGUAGE_TO_CONFIG.put("C#", Arrays.asList("project.json", "csproj"));
    }
    // Config file to dependency-framework mapping
    public static final Map<String, List<DependencyFramework>> CONFIG_TO_DEPENDENCY_FRAMEWORK = new HashMap<>();
    static {
        // Checker for package.json: look for dependency in quotes
        BiPredicate<String, String> packageJsonChecker = (content, dep) -> content.contains("\"" + dep + "\"");

        // Checker for pom.xml: look for dependency within <artifactId> tags
        BiPredicate<String, String> pomXmlChecker = (content, dep) -> content.contains("<artifactId>" + dep + "</artifactId>");

        // Checker for build.gradle: look for dependency in single or double quotes
        BiPredicate<String, String> gradleChecker = (content, dep) ->
                content.contains("'" + dep + "'") || content.contains("\"" + dep + "\"");

        // Checker for requirements.txt: look for dependency as a line
        BiPredicate<String, String> requirementsChecker = (content, dep) -> content.contains(dep);

        // Checker for pyproject.toml: look for dependency in quotes
        BiPredicate<String, String> pyprojectChecker = (content, dep) -> content.contains("\"" + dep + "\"");

        // Checker for composer.json: look for dependency in quotes
        BiPredicate<String, String> composerChecker = (content, dep) -> content.contains("\"" + dep + "\"");

        // Checker for Gemfile: look for gem dependency
        BiPredicate<String, String> gemfileChecker = (content, dep) -> content.contains("gem \"" + dep + "\"");

        // Checker for go.mod: look for dependency
        BiPredicate<String, String> goModChecker = (content, dep) -> content.contains(dep);

        // Checker for Cargo.toml: look for dependency in quotes
        BiPredicate<String, String> cargoChecker = (content, dep) -> content.contains(dep);

        // Checker for Package.swift: look for dependency
        BiPredicate<String, String> swiftChecker = (content, dep) -> content.contains(dep);

        // Checker for pubspec.yaml: look for dependency
        BiPredicate<String, String> pubspecChecker = (content, dep) -> content.contains(dep + ":");

        // Checker for .csproj/project.json: look for dependency
        BiPredicate<String, String> csprojChecker = (content, dep) -> content.contains("<PackageReference Include=\"" + dep + "\"");


        CONFIG_TO_DEPENDENCY_FRAMEWORK.put("package.json", Arrays.asList(
                new DependencyFramework("react", "React", packageJsonChecker),
                new DependencyFramework("express", "Express", packageJsonChecker),
                new DependencyFramework("next", "Next.js", packageJsonChecker),
                new DependencyFramework("vue", "Vue.js", packageJsonChecker),
                new DependencyFramework("nuxt", "Nuxt.js", packageJsonChecker),
                new DependencyFramework("nestjs", "NestJS", packageJsonChecker),
                new DependencyFramework("@angular/core", "Angular", packageJsonChecker),
                new DependencyFramework("svelte", "Svelte", packageJsonChecker),
                new DependencyFramework("remix", "Remix", packageJsonChecker)
        ));
        CONFIG_TO_DEPENDENCY_FRAMEWORK.put("pom.xml", Arrays.asList(
                new DependencyFramework("spring-boot-starter-web", "Spring Boot", pomXmlChecker)
        ));
        CONFIG_TO_DEPENDENCY_FRAMEWORK.put("build.gradle", Arrays.asList(
                new DependencyFramework("spring-boot-starter-web", "Spring Boot", gradleChecker)
        ));
        // Python frameworks (requirements.txt)
        CONFIG_TO_DEPENDENCY_FRAMEWORK.put("requirements.txt", Arrays.asList(
                new DependencyFramework("flask", "Flask", requirementsChecker),
                new DependencyFramework("django", "Django", requirementsChecker),
                new DependencyFramework("fastapi", "FastAPI", requirementsChecker)
        ));

        // Python frameworks (pyproject.toml)
        CONFIG_TO_DEPENDENCY_FRAMEWORK.put("pyproject.toml", Arrays.asList(
                new DependencyFramework("flask", "Flask", pyprojectChecker),
                new DependencyFramework("django", "Django", pyprojectChecker),
                new DependencyFramework("fastapi", "FastAPI", pyprojectChecker)
        ));

        // PHP frameworks (composer.json)
        CONFIG_TO_DEPENDENCY_FRAMEWORK.put("composer.json", Arrays.asList(
                new DependencyFramework("laravel/framework", "Laravel", composerChecker)
        ));

        // Ruby frameworks (Gemfile)
        CONFIG_TO_DEPENDENCY_FRAMEWORK.put("Gemfile", Arrays.asList(
                new DependencyFramework("rails", "Ruby on Rails", gemfileChecker)
        ));

        // Go frameworks (go.mod)
        CONFIG_TO_DEPENDENCY_FRAMEWORK.put("go.mod", Arrays.asList(
                new DependencyFramework("github.com/gin-gonic/gin", "Gin", goModChecker)
        ));

        // Rust frameworks (Cargo.toml)
        CONFIG_TO_DEPENDENCY_FRAMEWORK.put("Cargo.toml", Arrays.asList(
                new DependencyFramework("actix-web", "Actix Web", cargoChecker),
                new DependencyFramework("rocket", "Rocket", cargoChecker)
        ));

        // Swift frameworks (Package.swift)
        CONFIG_TO_DEPENDENCY_FRAMEWORK.put("Package.swift", Arrays.asList(
                new DependencyFramework("github.com/vapor/vapor", "Vapor", swiftChecker)
        ));

        // Dart/Flutter frameworks (pubspec.yaml)
        CONFIG_TO_DEPENDENCY_FRAMEWORK.put("pubspec.yaml", Arrays.asList(
                new DependencyFramework("flutter", "Flutter", pubspecChecker)
        ));

        // C# frameworks (csproj/project.json)
        CONFIG_TO_DEPENDENCY_FRAMEWORK.put("csproj", Arrays.asList(
                new DependencyFramework("Microsoft.AspNetCore", "ASP.NET Core", csprojChecker)
        ));
        CONFIG_TO_DEPENDENCY_FRAMEWORK.put("project.json", Arrays.asList(
                new DependencyFramework("Microsoft.AspNetCore", "ASP.NET Core", csprojChecker)
        ));


    }
    // Framework to file extensions mapping
    public static final Map<String, List<String>> FRAMEWORK_TO_FILE_EXTENSIONS = new HashMap<>();
    static {
        FRAMEWORK_TO_FILE_EXTENSIONS.put("React", Arrays.asList(".jsx", ".tsx"));
        FRAMEWORK_TO_FILE_EXTENSIONS.put("Express", Arrays.asList(".js", ".ts"));
        FRAMEWORK_TO_FILE_EXTENSIONS.put("Spring Boot", Arrays.asList(".java"));
        FRAMEWORK_TO_FILE_EXTENSIONS.put("Next.js", Arrays.asList(".jsx", ".tsx"));
        FRAMEWORK_TO_FILE_EXTENSIONS.put("Vue.js", Arrays.asList(".vue"));
        FRAMEWORK_TO_FILE_EXTENSIONS.put("Nuxt.js", Arrays.asList(".vue"));
        FRAMEWORK_TO_FILE_EXTENSIONS.put("NestJS", Arrays.asList(".ts"));
        FRAMEWORK_TO_FILE_EXTENSIONS.put("Angular", Arrays.asList(".ts"));
        FRAMEWORK_TO_FILE_EXTENSIONS.put("Svelte", Arrays.asList(".svelte"));
        FRAMEWORK_TO_FILE_EXTENSIONS.put("Remix", Arrays.asList(".jsx", ".tsx"));
        FRAMEWORK_TO_FILE_EXTENSIONS.put("Flask", Arrays.asList(".py"));
        FRAMEWORK_TO_FILE_EXTENSIONS.put("Django", Arrays.asList(".py"));
        FRAMEWORK_TO_FILE_EXTENSIONS.put("FastAPI", Arrays.asList(".py"));
        FRAMEWORK_TO_FILE_EXTENSIONS.put("Laravel", Arrays.asList(".php"));
        FRAMEWORK_TO_FILE_EXTENSIONS.put("Ruby on Rails", Arrays.asList(".rb"));
        FRAMEWORK_TO_FILE_EXTENSIONS.put("Gin", Arrays.asList(".go"));
        FRAMEWORK_TO_FILE_EXTENSIONS.put("Actix Web", Arrays.asList(".rs"));
        FRAMEWORK_TO_FILE_EXTENSIONS.put("Rocket", Arrays.asList(".rs"));
        FRAMEWORK_TO_FILE_EXTENSIONS.put("Vapor", Arrays.asList(".swift"));
        FRAMEWORK_TO_FILE_EXTENSIONS.put("Flutter", Arrays.asList(".dart"));
        FRAMEWORK_TO_FILE_EXTENSIONS.put("ASP.NET Core", Arrays.asList(".cs"));
    }
}
