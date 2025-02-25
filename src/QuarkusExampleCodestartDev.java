///usr/bin/env jbang "$0" "$@" ; exit $?
//SOURCES WrapperRunner.java
//DEPS info.picocli:picocli:4.5.0
//DEPS io.quarkus:quarkus-platform-descriptor-json:${quarkus.version:1.12.2.Final} io.quarkus:quarkus-platform-descriptor-resolver-json:${quarkus.version:1.12.2.Final}
//DEPS io.quarkus:quarkus-devtools-common:${quarkus.version:1.12.2.Final}
//DEPS org.eclipse.sisu:org.eclipse.sisu.plexus:0.3.4

import io.quarkus.devtools.codestarts.CodestartProjectDefinition;
import io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartCatalog;
import io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartProjectInput;
import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.devtools.project.BuildTool;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import io.quarkus.platform.descriptor.QuarkusPlatformDescriptor;
import io.quarkus.platform.descriptor.resolver.json.QuarkusJsonPlatformDescriptorResolver;
import io.quarkus.platform.tools.ToolsConstants;
import io.quarkus.platform.tools.ToolsUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartData.QuarkusDataKey.*;
import static io.quarkus.platform.tools.ToolsUtils.readQuarkusProperties;

@Command(name = "QuarkusExampleCodestartDev", mixinStandardHelpOptions = true, version = "0.1",
    description = "QuarkusExampleCodestartDev made with jbang")
class QuarkusExampleCodestartDev implements Callable<Integer> {
    private QuarkusPlatformDescriptor platformDescr;

    @Option(names = "-l", description = "comma separated list of languages to generate", defaultValue = "java")
    private String languages;

    @Option(names = "-b", description = "buildtool to use (maven, gradle, gradle-kotlin-dsl)", defaultValue = "maven")
    private String buildTool;

    @Option(names = "-c", description = "the directory containing one or more codestarts to develop", defaultValue = "./codestarts")
    private File codestartsDir;

    @Option(names = "-n", description = "the name of the project to generate", defaultValue = "dev-app")
    private String projName;

    @Option(names = "-t", description = "compile and run tests", defaultValue = "false")
    private boolean test;

    @Option(names = "-d", description = "enable debug", defaultValue = "false")
    private boolean debug;

    @Option(names = "-o", description = "the target directory where we generate the projects", defaultValue = "target")
    private File targetDir;

    @Parameters(index = "0", description = "comma separated list of codestarts to add")
    private String names;

    public static void main(String... args) {
        int exitCode = new CommandLine(new QuarkusExampleCodestartDev()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        for (String s : languages.split(",")) {
            String language = s.trim().toLowerCase();

            final QuarkusCodestartProjectInput input = QuarkusCodestartProjectInput.builder()
                .addData(getDefaultData(getPlatformDescriptor()))
                .buildTool(BuildTool.findTool(buildTool))
                .addCodestart(language)
                .addCodestarts(Arrays.stream(names.split(",")).map(String::trim).collect(Collectors.toList()))
                .putData(JAVA_VERSION.key(), System.getProperty("java.specification.version"))
                .messageWriter(debug ? MessageWriter.debug() : MessageWriter.info())
                .build();

            final CodestartProjectDefinition projectDefinition = getCatalog().createProject(input);
            final Path targetPath = targetDir.toPath().resolve(projName + "_" + language);
            if (Files.isDirectory(targetPath)) {
                System.out.println("Directory already exists '" + targetPath.toString() + "'.. delete [y]?");
                Scanner scanner = new Scanner(System.in);
                String res = scanner.nextLine();
                if (res.isBlank() || res.startsWith("y")) {
                    deleteDirectoryStream(targetPath);
                } else {
                    continue;
                }
            }
            projectDefinition.generate(targetPath);
            System.out.println("\n\nProject created in " + language + ": " + targetPath.toString() );

            if (test) {
                System.out.println("Building and running tests...");
                WrapperRunner.run(targetPath, WrapperRunner.Wrapper.fromBuildtool(buildTool));
            }
            System.out.println("---------------\n");
        }
        return 0;
    }

    static void deleteDirectoryStream(Path path) throws IOException {
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
    }

    static Map<String, Object> getDefaultData(final QuarkusPlatformDescriptor descriptor) {
        final HashMap<String, Object> data = new HashMap<>();
        final Properties quarkusProp = readQuarkusProperties(descriptor);
        data.put(BOM_GROUP_ID.key(), descriptor.getBomGroupId());
        data.put(BOM_ARTIFACT_ID.key(), descriptor.getBomArtifactId());
        data.put(BOM_VERSION.key(), descriptor.getBomVersion());
        data.put(QUARKUS_VERSION.key(), descriptor.getQuarkusVersion());
        data.put(QUARKUS_MAVEN_PLUGIN_GROUP_ID.key(), ToolsUtils.getMavenPluginGroupId(quarkusProp));
        data.put(QUARKUS_MAVEN_PLUGIN_ARTIFACT_ID.key(), ToolsUtils.getMavenPluginArtifactId(quarkusProp));
        data.put(QUARKUS_MAVEN_PLUGIN_VERSION.key(), ToolsUtils.getMavenPluginVersion(quarkusProp));
        data.put(QUARKUS_GRADLE_PLUGIN_ID.key(), ToolsUtils.getMavenPluginGroupId(quarkusProp));
        data.put(QUARKUS_GRADLE_PLUGIN_VERSION.key(), ToolsUtils.getGradlePluginVersion(quarkusProp));
        data.put(JAVA_VERSION.key(), "11");
        data.put(KOTLIN_VERSION.key(), quarkusProp.getProperty(ToolsConstants.PROP_KOTLIN_VERSION));
        data.put(SCALA_VERSION.key(), quarkusProp.getProperty(ToolsConstants.PROP_SCALA_VERSION));
        data.put(SCALA_MAVEN_PLUGIN_VERSION.key(), quarkusProp.getProperty(ToolsConstants.PROP_SCALA_PLUGIN_VERSION));
        data.put(MAVEN_COMPILER_PLUGIN_VERSION.key(), quarkusProp.getProperty(ToolsConstants.PROP_COMPILER_PLUGIN_VERSION));
        data.put(MAVEN_SUREFIRE_PLUGIN_VERSION.key(), quarkusProp.getProperty(ToolsConstants.PROP_SUREFIRE_PLUGIN_VERSION));
        return data;
    }

    private QuarkusCodestartCatalog getCatalog() throws IOException {
        return QuarkusCodestartCatalog.fromQuarkusPlatformDescriptorAndDirectories(getPlatformDescriptor(), Collections.singletonList(codestartsDir.toPath()));
    }

    private QuarkusPlatformDescriptor getPlatformDescriptor() {
        return platformDescr == null
            ? platformDescr = QuarkusJsonPlatformDescriptorResolver.newInstance().resolveBundled()
            : platformDescr;
    }
}
