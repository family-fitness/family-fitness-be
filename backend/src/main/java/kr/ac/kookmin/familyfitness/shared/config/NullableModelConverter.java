package kr.ac.kookmin.familyfitness.shared.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.AnnotationsUtils;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * jspecify {@link Nullable} 칸을 OpenAPI 3.1 스키마에 null 로 싣는다(QA CT-04).
 *
 * <p>swagger-core 도 이름이 {@code Nullable} 인 어노테이션을 보지만, Jackson 이 넘겨 주는 선언 어노테이션만 본다. jspecify 의
 * {@code @Nullable} 은 TYPE_USE 전용이라 그 목록에 들어오지 않고, 그래서 문서에서 null 이 모두 빠졌다. 여기서는 모델(클래스) 하나를
 * 풀 때마다 그 클래스의 record 컴포넌트 · 필드 · getter 의 {@link java.lang.reflect.AnnotatedType} 을 리플렉션으로 읽어 칸을 고른다.
 *
 * <p>판정 순서: ① 이 앱의 클래스인가 ② 칸의 타입에 {@code @Nullable} 이 붙었나 ③ 요청 검증({@code @NotNull} · {@code @NotBlank} ·
 * {@code @NotEmpty})이 null 을 막는가 — 막으면 null 을 싣지 않는다(검증 전 Java 값이 null 일 수 있어 붙인 {@code @Nullable} 이다).
 * 싣는 모양: 다른 스키마를 가리키는 칸($ref · 조합)은 {@code oneOf: [원래 칸, {type: null}]}, 그 밖은 {@code type} 에 "null" 을 더하고
 * enum 이면 목록에도 null 을 더한다. required 목록은 건드리지 않는다.
 *
 * <p>같은 모양의 칸 스키마를 swagger 가 여러 모델에서 나눠 쓸 수 있어, 원래 칸을 고치지 않고 복사본을 고쳐 바꿔 끼운다.
 */
public class NullableModelConverter implements ModelConverter {
    private static final String APP_PACKAGE = "kr.ac.kookmin.familyfitness.";
    private static final String NULL_TYPE = "null";
    private static final String REF_PREFIX = "#/components/schemas/";
    private static final List<Class<? extends Annotation>> REJECTS_NULL =
            List.of(NotNull.class, NotBlank.class, NotEmpty.class);

    @Override
    @SuppressWarnings("rawtypes")
    public @Nullable Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (!chain.hasNext()) return null;
        Schema resolved = chain.next().resolve(type, context, chain);
        if (resolved == null) return null;
        Class<?> raw = Json.mapper().constructType(type.getType()).getRawClass();
        if (!raw.getName().startsWith(APP_PACKAGE)) return resolved;
        Schema model = modelOf(resolved, context);
        if (model == null || model.getProperties() == null) return resolved;
        markNullable(model, nullablePropertiesOf(raw));
        return resolved;
    }

    @Override
    public boolean isOpenapi31() {
        return true;
    }

    /** 푼 결과가 참조($ref)면 이미 정의된 모델을 찾는다. */
    @SuppressWarnings("rawtypes")
    private static @Nullable Schema modelOf(Schema resolved, ModelConverterContext context) {
        if (resolved.getProperties() != null) return resolved;
        String ref = resolved.get$ref();
        String name =
                ref != null && ref.startsWith(REF_PREFIX) ? ref.substring(REF_PREFIX.length()) : resolved.getName();
        return name == null ? null : context.getDefinedModels().get(name);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void markNullable(Schema model, Set<String> nullable) {
        Map<String, Schema> properties = model.getProperties();
        for (String name : nullable) {
            Schema property = properties.get(name);
            if (property == null || allowsNull(property)) continue;
            properties.put(name, withNull(property));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean allowsNull(Schema property) {
        if (property.getTypes() != null && property.getTypes().contains(NULL_TYPE)) return true;
        List<Schema> oneOf = property.getOneOf();
        return oneOf != null
                && oneOf.stream()
                        .anyMatch(it -> it.getTypes() != null && it.getTypes().contains(NULL_TYPE));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema withNull(Schema property) {
        boolean composed = property.get$ref() != null || property.getAllOf() != null || property.getAnyOf() != null;
        if (composed || property.getOneOf() != null) {
            return new JsonSchema().oneOf(List.of(property, new JsonSchema().typesItem(NULL_TYPE)));
        }
        Schema copy = AnnotationsUtils.clone(property, true);
        Set<String> types = copy.getTypes();
        if (types == null || types.isEmpty()) {
            if (copy.getType() == null) return property; // 타입을 정하지 않은 칸({})은 이미 null 도 받는다
            copy.addType(copy.getType());
        }
        copy.addType(NULL_TYPE);
        if (copy.getEnum() != null && !copy.getEnum().contains(null)) copy.addEnumItemObject(null);
        return copy;
    }

    /** 이 클래스에서 null 이 될 수 있는 JSON 칸 이름. record 컴포넌트 · 필드 · getter 를 본다. */
    static Set<String> nullablePropertiesOf(Class<?> type) {
        Set<String> names = new LinkedHashSet<>();
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                Method accessor = component.getAccessor();
                if (isNullable(component.getAnnotatedType(), accessor)) {
                    names.add(jsonName(accessor, component.getName()));
                }
            }
        }
        for (Class<?> k = type; k != null && k != Object.class && !k.isRecord(); k = k.getSuperclass()) {
            for (Field field : k.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                if (isNullable(field.getAnnotatedType(), field)) names.add(jsonName(field, field.getName()));
            }
        }
        for (Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Method method : k.getDeclaredMethods()) {
                String property = getterProperty(method);
                if (property != null && isNullable(method.getAnnotatedReturnType(), method)) {
                    names.add(jsonName(method, property));
                }
            }
        }
        return names;
    }

    private static boolean isNullable(java.lang.reflect.AnnotatedType annotatedType, AnnotatedElement declaration) {
        if (!annotatedType.isAnnotationPresent(Nullable.class)) return false;
        for (Class<? extends Annotation> rejects : REJECTS_NULL) {
            if (annotatedType.isAnnotationPresent(rejects) || declaration.isAnnotationPresent(rejects)) return false;
        }
        return true;
    }

    /** getX() · isX() 모양의 인자 없는 인스턴스 메서드면 칸 이름(x), 아니면 null. */
    private static @Nullable String getterProperty(Method method) {
        if (Modifier.isStatic(method.getModifiers()) || method.isSynthetic() || method.isBridge()) return null;
        if (method.getParameterCount() != 0 || method.getReturnType() == void.class) return null;
        String name = method.getName();
        String bare;
        if (name.startsWith("get") && name.length() > 3) bare = name.substring(3);
        else if (name.startsWith("is") && name.length() > 2) bare = name.substring(2);
        else return null;
        return Character.toLowerCase(bare.charAt(0)) + bare.substring(1);
    }

    private static String jsonName(AnnotatedElement element, String fallback) {
        JsonProperty renamed = element.getAnnotation(JsonProperty.class);
        return renamed != null && !renamed.value().isEmpty() ? renamed.value() : fallback;
    }
}
