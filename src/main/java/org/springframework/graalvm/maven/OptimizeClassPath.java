package org.springframework.graalvm.maven;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import org.springframework.graalvm.maven.util.AntPathMatcher;

public class OptimizeClassPath {

    public static void main(String... args) throws Exception {
        String directory = args[0];
        String file = args[1];
        String classpath = args[2];

        System.out.println("Directory: "+directory);
        System.out.println("Histogram File: "+file);
        System.out.println("Classpath: "+classpath);

        List<Path> jars = loadJarFilesFromClassPath(directory, classpath);
        Set<String> classnames = readLoadedClassesFromHistogram(directory, file);
        optimizeJars(jars, classnames);
    }


    private static void optimizeJars(List<Path> jars, Set<String> classnames) {
        jars.stream()
            .forEach(jar -> optimizeJar(jar, classnames));
    }
    private static void optimizeJar(Path jar, Set<String> classnames) {
        Map<String, String> jarProperties = new HashMap<>();
        jarProperties.put("create", "false");

        System.out.println();
        if (jar.toString().contains("spring-graalvm-native-0.8.0-SNAPSHOT.jar")) {
            System.out.println("Skipping JAR: " + jar.toString());
            return;
        }
        else {
            System.out.println("Optimizing JAR: " + jar.toString());
        }

        Set<String> toBeDeleted = new HashSet<>();
        try (ZipFile zf  = new ZipFile(jar.toFile())) {
            zf.stream()
                .filter(ze -> ze.getName().endsWith(".class")
                || ze.getName().endsWith(".xml")
//                || ze.getName().endsWith(".properties")
                )
                .forEach(
                    ze -> {
                        boolean present = isClassPresent(classnames, ze.getName());
                        System.out.println("Class: "+ze.getName() +"; Present: "+present);
                        if (!present) {
                            toBeDeleted.add(ze.getName());
                        }

                    }
                );

        } catch (IOException e) {
            throw new RuntimeException(e);
        }



        URI jarDisk = URI.create("jar:file:"+jar.toString());
        try (FileSystem fs = FileSystems.newFileSystem(jarDisk, jarProperties)) {
            toBeDeleted.stream()
                .forEach(filename -> {
                    Path jarFilePath = fs.getPath(filename);
                    /* Execute Delete */
                    try {
                        Files.delete(jarFilePath);
                        System.out.println("Deleted: "+ jarFilePath + "; from "+jarDisk);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    private static boolean isClassPresent(Set<String> classnames, String classname) {
        boolean present = classnames.contains(classname) || alwaysPresent.contains(classname);
        if (present) {
            return true;
        }
        //if this is an inner class and the parent class is loaded
        //allow all inner class to remain
        int idx = classname.indexOf("$");
        if (idx > 0) {
            String innerClass = classname;
            classname = classname.replaceAll("\\$.*\\.class", ".class");
            present = classnames.contains(classname) || alwaysPresent.contains(classname);
            System.out.println("Inner class: " + innerClass + "; Present: "+present+"; Parent:"+classname);
        }
        if (present) {
            return true;
        }

        //find and match patterns
        AntPathMatcher matcher = new AntPathMatcher();
        for (String name : alwaysPresent) {
            if (name.contains("*")) {
                if (matcher.match(name, classname)) {
                    return true;
                }
            }
        }
        return false;
    }



    private static List<Path> loadJarFilesFromClassPath(String directory, String classpath) {
        List<Path> jars = Arrays.stream(classpath.split("\\:"))
            .filter(s -> s.endsWith(".jar") && !s.contains("spring-graal-native"))
            .map(s -> Paths.get(directory + "/" + s))
            .collect(Collectors.toList());

        jars.stream().forEach(p -> System.out.println("Path: "+p.toString()));

        return jars;
    }

    private static Set<String> readLoadedClassesFromHistogram(String directory, String file) throws IOException {
        Set<String> classes = new HashSet<>();
        BufferedReader reader = new BufferedReader(new FileReader(new File(directory, file)));
        String s;
        while ((s = reader.readLine()) != null) {
            String start = "Class-Agent-Transform:";
            if (s.startsWith(start)) {
                String classname = s.substring(start.length()+1);
                classes.add(classname);
                System.out.println("FILIP Identified Class=" + classname + " from "+file);
            }
        }

        return classes;
    }
    private static Set<String> alwaysPresent = new HashSet<>(
        Arrays.asList(
            "org/springframework/jdbc/CannotGetJdbcConnectionException.class" //spring.factories
            ,"javax/validation/ValidationException.class" //spring.factories
            ,"org/springframework/boot/diagnostics/FailureAnalysis.class" //service providers?
            ,"org/springframework/boot/json/JsonParser.class"
            ,"org/apache/commons/logging/LogFactoryService.class"
            ,"org/apache/commons/logging/LogFactory.class"

            ,"org/springframework/validation/FieldError.class" //reflection warning
            ,"org/springframework/validation/ObjectError.class" //reflection warning
            ,"com/fasterxml/jackson/databind/ObjectMapper.class" //reflection warning
            ,"org/springframework/context/support/DefaultMessageSourceResolvable.class" //reflection warning
            ,"org/springframework/util/concurrent/DelegatingCompletableFuture.class" //GRAAL ERROR?
            ,"org/springframework/context/MessageSourceResolvable.class" //reflection warning
            ,"com/fasterxml/jackson/databind/ObjectMapper.class" //reflection warning
            ,"javax/security/auth/message/config/AuthConfigProvider.class" //reflection warning
            ,"org/apache/coyote/UpgradeToken.class" //reflection warning
            ,"org/springframework/web/servlet/mvc/method/RequestMappingInfo.class" //reflection warning
            ,"org/springframework/web/servlet/ModelAndView.class" //reflection warning
            ,"org/apache/catalina/TrackedWebResource.class" //reflection warning
            ,"org/springframework/web/HttpRequestHandler.class" //reflection warning
            ,"org/springframework/web/servlet/handler/RequestMatchResult.class" //reflection warning
            ,"org/springframework/web/servlet/handler/AbstractHandlerMapping.class" //reflection warning
            ,"org/apache/catalina/authenticator/jaspic/PersistentProviderRegistrations.class" //reflection warning
            ,"org/springframework/web/bind/annotation/CrossOrigin.class" //reflection warning
            ,"org/springframework/web/method/annotation/SessionAttributesHandler.class" //reflection warning
            ,"org/springframework/web/server/ResponseStatusException.class" //reflection warning
            ,"org/springframework/web/context/request/async/AsyncRequestTimeoutException.class" //reflection warning
            ,"org/springframework/web/method/annotation/ModelFactory.class" //reflection warning
            ,"org/springframework/web/method/support/ModelAndViewContainer.class" //reflection warning
            ,"org/springframework/http/CacheControl.class" //reflection warning
            ,"org/springframework/boot/autoconfigure/web/servlet/error/DefaultErrorViewResolver.class" //reflection warning
            ,"org/springframework/boot/web/server/ErrorPageRegistrarBeanPostProcessor.class" //reflection warning
            ,"org/apache/tomcat/util/descriptor/web/ErrorPage.class" //reflection warning
            ,"com/fasterxml/jackson/databind/InjectableValues.class" //reflection warning
            ,"com/fasterxml/jackson/databind/jsontype/NamedType.class" //reflection warning
            ,"com/fasterxml/jackson/core/io/CharacterEscapes.class" //reflection warning
            ,"com/fasterxml/jackson/databind/jsonFormatVisitors/JsonFormatVisitorWrapper.class" //reflection warning
            ,"com/fasterxml/jackson/databind/module/SimpleValueInstantiators.class" //reflection warning
            ,"com/fasterxml/jackson/core/io/InputDecorator.class" //reflection warning
            ,"com/fasterxml/jackson/core/io/OutputDecorator.class" //reflection warning
            ,"com/fasterxml/jackson/databind/deser/ValueInstantiator.class" //reflection warning
            ,"com/fasterxml/jackson/databind/PropertyNamingStrategy.class" //reflection warning

            ,"ch/qos/logback/classic/servlet/LogbackServletContainerInitializer.class" //graal reflection error (service)
            ,"org/springframework/web/SpringServletContainerInitializer.class" //graal reflection error (service)
            ,"org/apache/logging/log4j/spi/LoggerContext.class" //graal NoClassDefFoundError
            ,"org/springframework/boot/BeanDefinitionLoader.class" //graal NoClassDefFoundError
            ,"org/springframework/boot/SpringBootExceptionHandler.class" //graal NoClassDefFoundError
            ,"org/springframework/beans/factory/support/BeanNameGenerator.class" //graal NoClassDefFoundError
            ,"org/springframework/boot/ExitCodeGenerator.class" //graal NoClassDefFoundError: [Lorg/springframework/boot/ExitCodeGenerator
            ,"org/springframework/web/method/annotation/ExceptionHandlerMethodResolver.class" //graal ClassNotFoundException
            ,"org/springframework/web/servlet/mvc/annotation/ModelAndViewResolver.class" //graal ClassNotFoundException
            ,"com/fasterxml/jackson/module/paramnames/ParameterNamesModule.class" //graal ClassNotFoundException
            ,"com/fasterxml/jackson/module/paramnames/ParameterNamesAnnotationIntrospector.class" //graal ClassNotFoundException
            ,"com/fasterxml/jackson/annotation/JsonCreator.class" //graal ClassNotFoundException
            ,"com/fasterxml/jackson/databind/ser/FilterProvider.class" //graal ClassNotFoundException
            ,"com/fasterxml/jackson/databind/util/LinkedNode.class" //graal ClassNotFoundException


            ,"org/apache/catalina/TomcatPrincipal.class" //runtime NoClassDefFoundError
            ,"org/springframework/context/i18n/TimeZoneAwareLocaleContext.class" //runtime NoClassDefFoundError


            ,"javax/servlet/http/LocalStrings.properties" //required resource
            ,"javax/servlet/LocalStrings.properties" //required resource
            ,"org/springframework/web/servlet/DispatcherServlet.properties" //required resource


            //new commits to master + sebastian optimized jafu-webmvc
            ,"org/springframework/core/io/support/EncodedResource.class" //graal NoClassDefFoundError
            ,"org/springframework/web/accept/ContentNegotiationManager.class" //graal spring-feature
            ,"org/springframework/web/method/ControllerAdviceBean.class" //graal spring-feature
            ,"org/springframework/web/servlet/mvc/method/annotation/ExceptionHandlerExceptionResolver.class" //graal spring-feature
            ,"org/springframework/web/servlet/mvc/method/annotation/Target_ExceptionHandlerExceptionResolver.class" //graal spring-feature
            ,"org/springframework/web/servlet/mvc/method/annotation/RequestMappingHandlerAdapter.class" //graal spring-feature
            ,"org/springframework/web/servlet/mvc/method/annotation/Target_RequestMappingHandlerAdapter.class" //graal spring-feature
            ,"org/springframework/web/servlet/config/annotation/WebMvcConfigurationSupport.class" //graal spring-feature
            ,"org/springframework/web/servlet/config/annotation/Target_WebMvcConfigurationSupport.class" //graal spring-feature
            ,"org/springframework/web/servlet/function/support/RouterFunctionMapping.class" //graal spring-feature
            ,"org/springframework/web/servlet/function/support/Target_RouterFunctionMapping.class" //graal spring-feature
            ,"org/apache/catalina/servlets/DefaultServlet.class" //graal spring-feature
            ,"org/apache/catalina/servlets/Target_DefaultServlet.class" //graal spring-feature
            ,"org/springframework/web/servlet/config/annotation/AsyncSupportConfigurer.class" //graal spring-feature
            ,"org/springframework/web/servlet/mvc/method/AbstractHandlerMethodAdapter.class" //graal spring-feature
            ,"org/springframework/web/servlet/config/annotation/ResourceHandlerRegistry.class" //graal spring-feature
            ,"org/springframework/web/servlet/support/WebContentGenerator.class" //graal spring-feature
            ,"org/springframework/web/servlet/mvc/method/annotation/RequestMappingHandlerMapping.class" //graal spring-feature
            ,"org/springframework/web/servlet/mvc/method/RequestMappingInfoHandlerMapping.class" //graal spring-feature
            ,"org/springframework/web/servlet/config/annotation/ViewControllerRegistry.class" //graal spring-feature
            ,"org/springframework/web/bind/support/ConfigurableWebBindingInitializer.class" //graal spring-feature
            ,"org/springframework/web/method/support/CompositeUriComponentsContributor.class" //graal spring-feature
            ,"org/springframework/web/servlet/handler/AbstractHandlerMethodMapping.class" //graal spring-feature
            ,"org/springframework/web/servlet/handler/AbstractHandlerMethodExceptionResolver.class" //graal spring-feature
            ,"org/springframework/web/method/support/UriComponentsContributor.class" //graal spring-feature
            ,"org/springframework/web/method/support/HandlerMethodArgumentResolverComposite.class" //graal spring-feature
            ,"org/springframework/web/method/support/HandlerMethodArgumentResolver.class" //graal spring-feature
            ,"org/springframework/web/method/support/HandlerMethodReturnValueHandlerComposite.class" //graal spring-feature
            ,"org/springframework/web/method/support/HandlerMethodReturnValueHandler.class" //graal spring-feature
            ,"org/springframework/web/bind/support/SessionAttributeStore.class" //graal spring-feature
            ,"org/springframework/web/HttpRequestMethodNotSupportedException.class" //graal spring-feature
            ,"org/springframework/web/HttpSessionRequiredException.class" //graal spring-feature
            ,"org/springframework/web/method/HandlerMethod.class" //graal spring-feature
            ,"org/springframework/web/servlet/mvc/method/annotation/ServletInvocableHandlerMethod.class" //graal spring-feature
            ,"org/springframework/web/method/support/InvocableHandlerMethod.class" //graal spring-feature
            ,"org/springframework/web/bind/support/WebDataBinderFactory.class" //graal spring-feature
            ,"org/springframework/web/method/annotation/InitBinderDataBinderFactory.class" //graal spring-feature
            ,"org/springframework/web/bind/support/DefaultDataBinderFactory.class" //graal spring-feature
            ,"org/springframework/web/method/annotation/InitBinderDataBinderFactory.class" //graal spring-feature
            ,"org/springframework/web/servlet/mvc/method/annotation/ServletRequestDataBinderFactory.class" //graal spring-feature
            ,"org/springframework/core/ReactiveAdapterRegistry.class" //graal spring-feature

//          MAKE SPRINGMVC-TOMCAT WORK



            //oh boy, let's just add it
            ,"org/apache/logging/log4j/**/*.class" //springmvc-tomcat
            ,"ch/qos/**/*.class" //springmvc-tomcat
            ,"org/springframework/boot/logging/**/*.class" //springmvc-tomcat

            ,"org/apache/tomcat/websocket/server/*.class" //jafu with websocket
            ,"org/apache/tomcat/websocket/*.class" //jafu with websocket
            ,"javax/websocket/*.class" //jafu with websocket
            ,"javax/websocket/server/*.class" //jafu with websocket
            ,"org/springframework/web/bind/MissingPathVariableException.class" //jafu with websocket
            ,"org/springframework/web/bind/MissingServletRequestParameterException.class" //jafu with websocket

            //discovered today, 6/8/20
//            ,"javax/validation/ValidationException.class" //springmvc-tomcat
//            ,"org/springframework/jdbc/CannotGetJdbcConnectionException.class" //springmvc-tomcat
            ,"org/springframework/boot/autoconfigure/admin/SpringApplicationAdminJmxAutoConfiguration.class" //springmvc-tomcat
            ,"**/Hints.class" //springmvc-tomcat
            ,"org/springframework/data/SpringDataComponentProcessor.class" //springmvc-tomcat
            ,"org/springframework/boot/autoconfigure/**/*AutoConfiguration.class" //springmvc-tomcat
            ,"org/springframework/boot/web/servlet/context/AnnotationConfigServletWebServerApplicationContext.class" //springmvc-tomcat
            ,"org/apache/catalina/realm/GenericPrincipal.class" //springmvc-tomcat
            ,"org/springframework/web/client/ResponseExtractor.class" //springmvc-tomcat
            ,"org/springframework/web/util/DefaultUriBuilderFactory.class" //springmvc-tomcat
            ,"org/springframework/web/util/UriBuilderFactory.class" //springmvc-tomcat
            ,"org/springframework/http/client/ClientHttpRequestFactory.class" //springmvc-tomcat
            ,"org/springframework/web/client/ResourceAccessException.class" //springmvc-tomcat
            ,"org/springframework/web/client/RestClientException.class" //springmvc-tomcat
            ,"org/springframework/web/client/RequestCallback.class" //springmvc-tomcat
            ,"org/springframework/http/RequestEntity.class" //springmvc-tomcat
            ,"org/springframework/http/client/ClientHttpResponse.class" //springmvc-tomcat
            ,"org/springframework/core/io/ProtocolResolver.class" //springmvc-tomcat
            ,"org/apache/tomcat/websocket/pojo/PojoMethodMapping.class" //springmvc-tomcat
            ,"org/apache/tomcat/util/net/SSLHostConfigCertificate.class" //springmvc-tomcat
            ,"org/apache/tomcat/util/net/openssl/ciphers/Cipher.class" //springmvc-tomcat
            ,"org/springframework/beans/factory/config/BeanDefinitionCustomizer.class" //springmvc-tomcat
            ,"com/fasterxml/jackson/databind/ObjectMapper.class" //springmvc-tomcat
            ,"javax/servlet/WriteListener.class" //springmvc-tomcat
            ,"javax/servlet/ReadListener.class" //springmvc-tomcat
            ,"org/apache/coyote/http11/upgrade/InternalHttpUpgradeHandler.class" //springmvc-tomcat
            ,"org/apache/tomcat/util/net/openssl/OpenSSLConf.class" //springmvc-tomcat
            ,"org/apache/catalina/SessionListener.class" //springmvc-tomcat
            ,"org/apache/catalina/Cluster.class" //springmvc-tomcat
            ,"org/apache/catalina/core/NamingContextListener.class" //springmvc-tomcat
            ,"org/apache/tomcat/util/net/SSLContext.class" //springmvc-tomcat
            ,"javax/servlet/http/WebConnection.class" //springmvc-tomcat
            ,"org/apache/catalina/SessionEvent.class" //springmvc-tomcat
            ,"com/fasterxml/jackson/core/format/InputAccessor.class" //springmvc-tomcat
            ,"com/fasterxml/jackson/databind/ObjectMapper$DefaultTypeResolverBuilder.class" //springmvc-tomcat
            ,"org/springframework/boot/ExitCodeGenerators.class" //springmvc-tomcat
            ,"com/fasterxml/jackson/core/format/MatchStrength.class" //springmvc-tomcat
            ,"org/apache/tomcat/util/net/SSLSupport.class" //springmvc-tomcat
            ,"org/apache/tomcat/util/descriptor/web/MessageDestination.class" //springmvc-tomcat
            ,"org/apache/tomcat/util/net/SSLSupport.class" //springmvc-tomcat
            ,"org/apache/tomcat/util/net/openssl/OpenSSLImplementation.class" //springmvc-tomcat
            ,"org/springframework/boot/ExitCodeExceptionMapper.class" //springmvc-tomcat
            ,"org/apache/tomcat/util/net/openssl/OpenSSLConfCmd.class" //springmvc-tomcat
            ,"com/fasterxml/jackson/core/io/IOContext.class" //springmvc-tomcat
            ,"org/springframework/core/annotation/TypeMappedAnnotation.class" //springmvc-tomcat
            ,"com/fasterxml/jackson/core/util/BufferRecycler.class" //springmvc-tomcat
            ,"org/springframework/boot/context/properties/ConfigurationProperties.class" //springmvc-tomcat
            ,"org/apache/catalina/startup/Catalina.class" //springmvc-tomcat
            ,"org/apache/catalina/loader/ResourceEntry.class" //springmvc-tomcat
            ,"org/springframework/beans/factory/support/AutowireCandidateQualifier.class" //springmvc-tomcat
            ,"org/springframework/web/cors/UrlBasedCorsConfigurationSource.class" //springmvc-tomcat
            ,"org/apache/tomcat/PeriodicEventListener.class" //springmvc-tomcat
            ,"org/apache/tomcat/util/http/parser/TokenList.class" //springmvc-tomcat
            ,"javax/servlet/ServletRequestWrapper.class" //springmvc-tomcat
            ,"javax/servlet/ServletResponseWrapper.class" //springmvc-tomcat
            ,"org/apache/catalina/core/ApplicationHttpRequest.class" //springmvc-tomcat
            ,"org/apache/tomcat/util/http/parser/AcceptLanguage.class" //springmvc-tomcat
        )
    );
}
