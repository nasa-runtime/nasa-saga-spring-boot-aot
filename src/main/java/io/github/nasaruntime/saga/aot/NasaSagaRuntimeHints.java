package io.github.nasaruntime.saga.aot;

import io.github.nasaruntime.core.aot.NasaAotApplicationPackages;
import io.github.nasaruntime.core.aot.NasaAotClassScanner;
import io.github.nasaruntime.saga.Saga;
import io.github.nasaruntime.saga.SagaStep;
import io.github.nasaruntime.saga.rc.SagaPayload;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 业务作用：补充 nasa-saga 模型绑定、持久化资源、MyBatis mapper 与应用 participant 的原生镜像提示。
 */
public final class NasaSagaRuntimeHints implements RuntimeHintsRegistrar, BeanFactoryInitializationAotProcessor {

    private static final String SAGA_PACKAGE = "io.github.nasaruntime.saga";
    private static final String MYBATIS_PACKAGE = SAGA_PACKAGE + ".mybatis";
    private static final String MYBATIS_CLASS = "org.apache.ibatis.session.SqlSession";
    private static final String MYBATIS_MAPPER = MYBATIS_PACKAGE + ".SagaMybatisMapper";
    private static final String MYBATIS_ANNOTATION_PACKAGE = "org.apache.ibatis.annotations";
    private static final Set<String> MYBATIS_PROVIDER_ANNOTATIONS = Set.of(
            "SelectProvider", "InsertProvider", "UpdateProvider", "DeleteProvider");
    private static final List<String> MYBATIS_REFLECTION_TYPES = List.of(
            "org.apache.ibatis.scripting.defaults.RawLanguageDriver",
            "org.apache.ibatis.scripting.xmltags.XMLLanguageDriver",
            "org.apache.ibatis.javassist.util.proxy.RuntimeSupport",
            "org.apache.ibatis.javassist.util.proxy.ProxyFactory",
            "org.apache.ibatis.logging.slf4j.Slf4jImpl",
            "org.apache.ibatis.logging.Log",
            "org.apache.ibatis.logging.commons.JakartaCommonsLoggingImpl",
            "org.apache.ibatis.logging.log4j2.Log4j2Impl",
            "org.apache.ibatis.logging.jdk14.Jdk14LoggingImpl",
            "org.apache.ibatis.logging.stdout.StdOutImpl",
            "org.apache.ibatis.logging.nologging.NoLoggingImpl",
            "org.apache.ibatis.session.SqlSessionFactory",
            "org.apache.ibatis.cache.impl.PerpetualCache",
            "org.apache.ibatis.cache.decorators.FifoCache",
            "org.apache.ibatis.cache.decorators.LruCache",
            "org.apache.ibatis.cache.decorators.SoftCache",
            "org.apache.ibatis.cache.decorators.WeakCache",
            "org.apache.ibatis.reflection.SystemMetaObject",
            "org.apache.ibatis.reflection.MetaObject",
            "org.apache.ibatis.type.EnumTypeHandler",
            "org.apache.ibatis.type.EnumOrdinalTypeHandler",
            "java.util.ArrayList",
            "java.util.HashMap",
            "java.util.TreeSet",
            "java.util.HashSet");
    private static final String MYBATIS_STATEMENT_HANDLER = "org.apache.ibatis.executor.statement.StatementHandler";
    private static final String MYBATIS_EXECUTOR = "org.apache.ibatis.executor.Executor";
    private static final BindingReflectionHintsRegistrar BINDING_HINTS = new BindingReflectionHintsRegistrar();
    private static final List<String> PHASE_METHODS = List.of("execute", "compensate", "cancel", "resolve");

    /**
     * 业务作用：登记 nasa-saga 自身模型、SQL 资源，并在 MyBatis 存在时登记 mapper 代理。
     *
     * @param hints       当前 AOT 提示集合
     * @param classLoader 应用类加载器
     *                    返回：无返回值；静态框架合同写入当前构建上下文。
     */
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        registerSagaModels(hints, classLoader);
        if (ClassUtils.isPresent(MYBATIS_CLASS, classLoader)) {
            registerMybatisTypes(hints, classLoader);
            hints.proxies().registerJdkProxy(TypeReference.of(MYBATIS_MAPPER));
        }
        hints.resources().registerPattern("db/*.sql");
    }

    /**
     * 业务作用：在 BeanFactory AOT 阶段冻结应用包范围，随后登记非 Bean 的 `@Saga` participant 反射合同。
     *
     * @param beanFactory 应用 BeanFactory
     * @return 负责扫描并登记 participant 的 AOT contribution
     */
    @Override
    public BeanFactoryInitializationAotContribution processAheadOfTime(
            ConfigurableListableBeanFactory beanFactory) {
        Set<String> packages = NasaAotApplicationPackages.resolve(beanFactory);
        ClassLoader classLoader = beanFactory.getBeanClassLoader();
        return (generationContext, beanFactoryInitializationCode) -> {
            RuntimeHints hints = generationContext.getRuntimeHints();
            registerParticipants(hints, classLoader, packages);
            if (ClassUtils.isPresent(MYBATIS_CLASS, classLoader)) {
                registerApplicationMybatisMappers(hints, classLoader, packages);
            }
        };
    }

    /**
     * 业务作用：登记 nasa-saga 的协议 record、枚举与私有 payload codec，避免把 transport 执行路径整体开放反射。
     *
     * @param hints       当前 AOT 提示集合
     * @param classLoader 应用类加载器
     *                    返回：无返回值；Jackson 实际绑定的模型和 codec 写入 reflection hints。
     */
    private static void registerSagaModels(RuntimeHints hints, ClassLoader classLoader) {
        for (Class<?> type : NasaAotClassScanner.scan(
                classLoader,
                Set.of(SAGA_PACKAGE),
                Set.of("io.github.nasaruntime.saga.aot", MYBATIS_PACKAGE))) {
            if (type.isRecord() || type.isEnum()) {
                BINDING_HINTS.registerReflectionHints(hints.reflection(), type);
            }
        }
        for (Class<?> codec : SagaPayload.class.getDeclaredClasses()) {
            hints.reflection().registerType(
                    codec, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
    }

    /**
     * 业务作用：仅在 MyBatis 可用时登记本地持久化 adapter 类型，保证 mapper 与 SQL provider 可被框架动态访问。
     *
     * @param hints       当前 AOT 提示集合
     * @param classLoader 应用类加载器
     *                    返回：无返回值；MyBatis 不存在时本方法不会被调用。
     */
    private static void registerMybatisTypes(RuntimeHints hints, ClassLoader classLoader) {
        for (Class<?> type : NasaAotClassScanner.scan(classLoader, Set.of(MYBATIS_PACKAGE))) {
            hints.reflection().registerType(type, MemberCategory.values());
        }
        for (String type : MYBATIS_REFLECTION_TYPES) {
            hints.reflection().registerType(TypeReference.of(type), MemberCategory.values());
        }
        hints.proxies().registerJdkProxy(
                TypeReference.of(MYBATIS_STATEMENT_HANDLER), TypeReference.of(MYBATIS_EXECUTOR));
        hints.proxies().registerJdkProxy(TypeReference.of(MYBATIS_STATEMENT_HANDLER));
        hints.proxies().registerJdkProxy(TypeReference.of(MYBATIS_EXECUTOR));
        hints.proxies().registerJdkProxy(
                TypeReference.of("org.apache.ibatis.executor.resultset.ResultSetHandler"));
        hints.proxies().registerJdkProxy(
                TypeReference.of("org.apache.ibatis.executor.parameter.ParameterHandler"));
        registerMybatisMethods(hints, classLoader, MYBATIS_STATEMENT_HANDLER);
        registerMybatisMethods(hints, classLoader, MYBATIS_EXECUTOR);
        for (String type : List.of(
                "org.apache.ibatis.mapping.BoundSql",
                "org.apache.ibatis.executor.statement.RoutingStatementHandler",
                "org.apache.ibatis.executor.statement.BaseStatementHandler")) {
            hints.reflection().registerType(TypeReference.of(type), MemberCategory.DECLARED_FIELDS);
        }
        hints.resources().registerPattern("org/apache/ibatis/builder/xml/*.dtd");
        hints.resources().registerPattern("org/apache/ibatis/builder/xml/*.xsd");
        hints.resources().registerPattern("mapper/*.xml");
    }

    /**
     * 业务作用：登记 MyBatis 插件代理会调用的完整接口方法，保持拦截链在原生镜像中的调用能力。
     *
     * @param hints       当前 AOT 提示集合
     * @param classLoader 应用类加载器
     * @param typeName    MyBatis 插件接口名称
     *                    返回：无返回值；接口不存在时由调用方的 MyBatis 存在性门禁保证不会进入。
     */
    private static void registerMybatisMethods(
            RuntimeHints hints, ClassLoader classLoader, String typeName) {
        Class<?> type = ClassUtils.resolveClassName(typeName, classLoader);
        for (Method method : type.getMethods()) {
            if (method.getDeclaringClass() != Object.class) {
                hints.reflection().registerMethod(method, ExecutableMode.INVOKE);
            }
        }
    }

    /**
     * 业务作用：扫描应用基础包中的 `@Saga` participant，登记描述符检查所需方法和托管构造器。
     *
     * @param hints       当前 AOT 提示集合
     * @param classLoader 应用类加载器
     * @param packages    已校验的应用基础包
     *                    返回：无返回值；命中的 participant 反射入口写入 hints。
     */
    static void registerParticipants(
            RuntimeHints hints, ClassLoader classLoader, Set<String> packages) {
        for (Class<?> type : NasaAotClassScanner.scan(classLoader, packages)) {
            Saga annotation = AnnotatedElementUtils.findMergedAnnotation(type, Saga.class);
            if (annotation == null) {
                continue;
            }
            if (!SagaStep.class.isAssignableFrom(type)) {
                throw new IllegalStateException("@Saga type must implement SagaStep: " + type.getName());
            }
            hints.reflection().registerType(
                    type,
                    MemberCategory.INTROSPECT_DECLARED_METHODS,
                    MemberCategory.INTROSPECT_DECLARED_CONSTRUCTORS);
            registerPhaseMethods(hints, type);
            if (annotation.managed()) {
                registerManagedConstructor(hints, type);
            }
        }
    }

    /**
     * 业务作用：发现应用包内使用 MyBatis 注解的 mapper 接口，并登记运行期创建 mapper 实例所需的 JDK proxy。
     *
     * @param hints       当前 AOT 提示集合
     * @param classLoader 应用类加载器
     * @param packages    已校验的应用基础包
     *                    返回：无返回值；只登记具有 MyBatis 方法或类型注解的业务接口。
     */
    static void registerApplicationMybatisMappers(
            RuntimeHints hints, ClassLoader classLoader, Set<String> packages) {
        for (Class<?> type : NasaAotClassScanner.scan(classLoader, packages)) {
            if (type.isInterface() && isMybatisMapper(type)) {
                hints.reflection().registerType(type, MemberCategory.values());
                hints.proxies().registerJdkProxy(type);
                registerMapperRelationships(hints, type);
            }
        }
    }

    /**
     * 业务作用：登记 Mapper 方法关联的 SQL provider、参数模型与返回模型，使注解 SQL 的完整绑定链可达。
     *
     * @param hints           当前 AOT 提示集合
     * @param mapperInterface 已确认使用 MyBatis 注解的业务 Mapper
     *                        返回：无返回值；所有非 JDK 业务关系类型写入 reflection hints。
     */
    private static void registerMapperRelationships(RuntimeHints hints, Class<?> mapperInterface) {
        for (Method method : mapperInterface.getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            registerSqlProviderTypes(hints, method);
            registerMapperBindingType(hints, method.getGenericReturnType());
            for (Type parameterType : method.getGenericParameterTypes()) {
                registerMapperBindingType(hints, parameterType);
            }
        }
    }

    /**
     * 业务作用：读取 MyBatis provider 注解声明的生成器类型，保留动态选取 SQL 方法所需的成员。
     *
     * @param hints  当前 AOT 提示集合
     * @param method Mapper 方法
     *               返回：无返回值；未使用 provider 注解的方法不产生额外提示。
     */
    private static void registerSqlProviderTypes(RuntimeHints hints, Method method) {
        for (Annotation annotation : method.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (!MYBATIS_ANNOTATION_PACKAGE.equals(annotationType.getPackageName())
                    || !MYBATIS_PROVIDER_ANNOTATIONS.contains(annotationType.getSimpleName())) {
                continue;
            }
            for (String attribute : List.of("value", "type")) {
                try {
                    Object provider = annotationType.getMethod(attribute).invoke(annotation);
                    if (provider instanceof Class<?> providerType && providerType != void.class) {
                        registerApplicationReflectionType(hints, providerType);
                    }
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException(
                            "Cannot read MyBatis provider from " + method.toGenericString(), error);
                }
            }
        }
    }

    /**
     * 业务作用：展开 Mapper 泛型签名并登记其中的业务绑定类型，覆盖集合、Map、数组和泛型边界。
     *
     * @param hints 当前 AOT 提示集合
     * @param type  Mapper 参数或返回值的泛型签名
     *              返回：无返回值；JDK 容器本身跳过，其承载的业务类型仍会登记。
     */
    private static void registerMapperBindingType(RuntimeHints hints, Type type) {
        registerMapperBindingType(hints, type, new HashSet<>());
    }

    /**
     * 业务作用：以循环保护递归解析泛型关系，防止自引用类型变量导致 AOT 阶段无限展开。
     *
     * @param hints   当前 AOT 提示集合
     * @param type    当前待解析的泛型节点
     * @param visited 当前方法签名内已解析的节点
     *                返回：无返回值；重复节点直接跳过。
     */
    private static void registerMapperBindingType(
            RuntimeHints hints, Type type, Set<Type> visited) {
        if (type == null || !visited.add(type)) {
            return;
        }
        if (type instanceof Class<?> bindingType) {
            if (bindingType.isArray()) {
                registerMapperBindingType(hints, bindingType.getComponentType(), visited);
            } else {
                registerApplicationReflectionType(hints, bindingType);
            }
            return;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            registerMapperBindingType(hints, parameterizedType.getOwnerType(), visited);
            registerMapperBindingType(hints, parameterizedType.getRawType(), visited);
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                registerMapperBindingType(hints, argument, visited);
            }
            return;
        }
        if (type instanceof GenericArrayType arrayType) {
            registerMapperBindingType(hints, arrayType.getGenericComponentType(), visited);
            return;
        }
        if (type instanceof WildcardType wildcardType) {
            for (Type bound : wildcardType.getUpperBounds()) {
                registerMapperBindingType(hints, bound, visited);
            }
            for (Type bound : wildcardType.getLowerBounds()) {
                registerMapperBindingType(hints, bound, visited);
            }
            return;
        }
        if (type instanceof TypeVariable<?> variable) {
            for (Type bound : variable.getBounds()) {
                registerMapperBindingType(hints, bound, visited);
            }
        }
    }

    /**
     * 业务作用：只为可由应用声明的非 JDK 类型开放完整反射，避免扩大基础平台类型的访问面。
     *
     * @param hints 当前 AOT 提示集合
     * @param type  SQL provider 或 Mapper 绑定类型
     *              返回：无返回值；基础类型、JDK 类型和 void 被忽略。
     */
    private static void registerApplicationReflectionType(RuntimeHints hints, Class<?> type) {
        if (!type.isPrimitive() && type != void.class && !type.getName().startsWith("java.")) {
            hints.reflection().registerType(type, MemberCategory.values());
        }
    }

    /**
     * 业务作用：依据 MyBatis 公共注解合同识别 mapper，不把普通业务接口扩大为动态代理入口。
     *
     * @param type 候选应用接口
     * @return 类型或其公开方法携带 MyBatis 注解时返回 true
     */
    private static boolean isMybatisMapper(Class<?> type) {
        if (hasMybatisAnnotation(type.getAnnotations())) {
            return true;
        }
        for (Method method : type.getMethods()) {
            if (hasMybatisAnnotation(method.getAnnotations())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 业务作用：把注解来源严格限定在 MyBatis 公共注解包，避免依赖具体 SQL 注解清单。
     *
     * @param annotations 候选类型或方法的运行期注解
     * @return 至少一个注解来自 MyBatis 公共注解包时返回 true
     */
    private static boolean hasMybatisAnnotation(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (MYBATIS_ANNOTATION_PACKAGE.equals(annotation.annotationType().getPackageName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 业务作用：登记 participant 显式声明的阶段方法，使描述符可核验方法签名且运行期保留调用入口。
     *
     * @param hints 当前 AOT 提示集合
     * @param type  已确认实现 SagaStep 的 participant 类型
     *              返回：无返回值；当前类型声明的阶段方法写入 hints。
     */
    private static void registerPhaseMethods(RuntimeHints hints, Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (PHASE_METHODS.contains(method.getName())) {
                hints.reflection().registerMethod(method, ExecutableMode.INVOKE);
            }
        }
    }

    /**
     * 业务作用：为 `managed=true` participant 登记无参构造器，保证运行期能够创建受托管实例。
     *
     * @param hints 当前 AOT 提示集合
     * @param type  注解声明由 participant runtime 托管的类型
     *              返回：无返回值；缺少无参构造器时终止 AOT 构建并指出类型。
     */
    private static void registerManagedConstructor(RuntimeHints hints, Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            hints.reflection().registerConstructor(constructor, ExecutableMode.INVOKE);
        } catch (NoSuchMethodException error) {
            throw new IllegalStateException(
                    "Managed @Saga type requires a no-arg constructor: " + type.getName(), error);
        }
    }
}
