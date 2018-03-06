/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.mapping.context;

import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.PropertyHandler;
import org.springframework.data.mapping.PropertyPath;
import org.springframework.data.util.ClassTypeInformation;
import org.springframework.data.util.Pair;
import org.springframework.data.util.Streamable;
import org.springframework.data.util.TypeInformation;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.StringUtils;

/**
 * A factory implementation to create {@link PersistentPropertyPath} instances in various ways.
 * 
 * @author Oliver Gierke
 * @since 2.1
 * @soundtrack Cypress Hill - Boom Biddy Bye Bye (Fugges Remix, Unreleased & Revamped)
 */
@RequiredArgsConstructor
public class PersistentPropertyPathFactory<E extends PersistentEntity<?, P>, P extends PersistentProperty<P>> {

	private final Map<TypeAndPath, PersistentPropertyPath<P>> propertyPaths = new ConcurrentReferenceHashMap<>();

	private final MappingContext<E, P> context;

	/**
	 * Creates a new {@link PersistentPropertyPath} for the given property path on the given type.
	 * 
	 * @param type must not be {@literal null}.
	 * @param propertyPath must not be {@literal null}.
	 * @return
	 */
	public PersistentPropertyPath<P> from(Class<?> type, String propertyPath) {

		Assert.notNull(type, "Type must not be null!");
		Assert.notNull(propertyPath, "Property path must not be null!");

		return getPersistentPropertyPath(ClassTypeInformation.from(type), propertyPath);
	}

	/**
	 * Creates a new {@link PersistentPropertyPath} for the given property path on the given type.
	 * 
	 * @param type must not be {@literal null}.
	 * @param propertyPath must not be {@literal null}.
	 * @return
	 */
	public PersistentPropertyPath<P> from(TypeInformation<?> type, String propertyPath) {

		Assert.notNull(type, "Type must not be null!");
		Assert.notNull(propertyPath, "Property path must not be null!");

		return getPersistentPropertyPath(type, propertyPath);
	}

	/**
	 * Creates a new {@link PersistentPropertyPath} for the given {@link PropertyPath}.
	 * 
	 * @param path must not be {@literal null}.
	 * @return
	 */
	public PersistentPropertyPath<P> from(PropertyPath path) {

		Assert.notNull(path, "Property path must not be null!");

		return from(path.getOwningType(), path.toDotPath());
	}

	/**
	 * Creates a new {@link PersistentPropertyPath} based on a given type and {@link Predicate} to select properties
	 * matching it.
	 * 
	 * @param type must not be {@literal null}.
	 * @param propertyFilter must not be {@literal null}.
	 * @return
	 */
	public Streamable<PersistentPropertyPath<P>> from(TypeInformation<?> type, Predicate<P> propertyFilter) {

		Assert.notNull(type, "Type must not be null!");
		Assert.notNull(propertyFilter, "Property filter must not be null!");

		return Streamable.of(from(type, propertyFilter, DefaultPersistentPropertyPath.empty()));
	}

	private PersistentPropertyPath<P> getPersistentPropertyPath(TypeInformation<?> type, String propertyPath) {

		return propertyPaths.computeIfAbsent(TypeAndPath.of(type, propertyPath),
				it -> createPersistentPropertyPath(it.getPath(), it.getType()));
	}

	/**
	 * Creates a {@link PersistentPropertyPath} for the given parts and {@link TypeInformation}.
	 *
	 * @param propertyPath must not be {@literal null}.
	 * @param type must not be {@literal null}.
	 * @return
	 */
	private PersistentPropertyPath<P> createPersistentPropertyPath(String propertyPath, TypeInformation<?> type) {

		List<String> parts = Arrays.asList(propertyPath.split("\\."));
		DefaultPersistentPropertyPath<P> path = DefaultPersistentPropertyPath.empty();
		Iterator<String> iterator = parts.iterator();
		E current = context.getRequiredPersistentEntity(type);

		while (iterator.hasNext()) {

			String segment = iterator.next();
			final DefaultPersistentPropertyPath<P> foo = path;
			final E bar = current;

			Pair<DefaultPersistentPropertyPath<P>, E> pair = getPair(path, iterator, segment, current);

			if (pair == null) {

				String source = StringUtils.collectionToDelimitedString(parts, ".");
				String resolvedPath = foo.toDotPath();

				throw new InvalidPersistentPropertyPath(source, type, segment, resolvedPath,
						String.format("No property %s found on %s!", segment, bar.getName()));
			}

			path = pair.getFirst();
			current = pair.getSecond();
		}

		return path;
	}

	@Nullable
	private Pair<DefaultPersistentPropertyPath<P>, E> getPair(DefaultPersistentPropertyPath<P> path,
			Iterator<String> iterator, String segment, E entity) {

		P property = entity.getPersistentProperty(segment);

		if (property == null) {
			return null;
		}

		TypeInformation<?> type = property.getTypeInformation().getRequiredActualType();
		return Pair.of(path.append(property), iterator.hasNext() ? context.getRequiredPersistentEntity(type) : entity);
	}

	private Collection<PersistentPropertyPath<P>> from(TypeInformation<?> type, Predicate<P> filter,
			DefaultPersistentPropertyPath<P> basePath) {

		TypeInformation<?> actualType = type.getActualType();

		if (actualType == null) {
			return Collections.emptyList();
		}

		E entity = context.getRequiredPersistentEntity(actualType);

		Set<PersistentPropertyPath<P>> properties = new HashSet<>();

		PropertyHandler<P> propertyTester = persistentProperty -> {

			TypeInformation<?> typeInformation = persistentProperty.getTypeInformation();
			TypeInformation<?> actualPropertyType = typeInformation.getActualType();

			if (basePath.containsPropertyOfType(actualPropertyType)) {
				return;
			}

			DefaultPersistentPropertyPath<P> currentPath = basePath.append(persistentProperty);

			if (filter.test(persistentProperty)) {
				properties.add(currentPath);
			}

			if (persistentProperty.isEntity()) {
				properties.addAll(from(typeInformation, filter, currentPath));
			}
		};

		entity.doWithProperties(propertyTester);

		return properties;
	}

	@Value(staticConstructor = "of")
	static class TypeAndPath {

		TypeInformation<?> type;
		String path;
	}
}
