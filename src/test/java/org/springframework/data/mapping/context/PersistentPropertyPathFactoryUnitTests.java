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

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import javax.inject.Inject;

import org.junit.Test;
import org.springframework.data.mapping.MappingException;
import org.springframework.data.mapping.PropertyPath;
import org.springframework.data.mapping.model.BasicPersistentEntity;
import org.springframework.data.util.ClassTypeInformation;
import org.springframework.data.util.Streamable;
import org.springframework.util.StringUtils;

/**
 * Unit tests for {@link PersistentPropertyPathFactory}.
 * 
 * @author Oliver Gierke
 * @soundtrack Cypress Hill - Illusions (Q-Tip Remix, Unreleased & Revamped)
 */
public class PersistentPropertyPathFactoryUnitTests {

	PersistentPropertyPathFactory<BasicPersistentEntity<Object, SamplePersistentProperty>, SamplePersistentProperty> factory = //
			new PersistentPropertyPathFactory<>(new SampleMappingContext());

	@Test
	public void doesNotTryToLookupPersistentEntityForLeafProperty() {
		assertThat(factory.from(Person.class, "name")).isNotNull();
	}

	@Test // DATACMNS-380
	public void returnsPersistentPropertyPathForDotPath() {

		PersistentPropertyPath<SamplePersistentProperty> path = factory.from(PersonSample.class, "persons.name");

		assertThat(path.getLength()).isEqualTo(2);
		assertThat(path.getBaseProperty().getName()).isEqualTo("persons");
		assertThat(path.getLeafProperty().getName()).isEqualTo("name");
	}

	@Test(expected = MappingException.class) // DATACMNS-380
	public void rejectsInvalidPropertyReferenceWithMappingException() {
		factory.from(PersonSample.class, "foo");
	}

	@Test // DATACMNS-695
	public void persistentPropertyPathTraversesGenericTypesCorrectly() {
		assertThat(factory.from(PropertyPath.from("field.wrapped.field", Outer.class))).hasSize(3);
	}

	@Test // DATACMNS-727
	public void exposesContextForFailingPropertyPathLookup() {

		assertThatExceptionOfType(InvalidPersistentPropertyPath.class)//
				.isThrownBy(() -> factory.from(PersonSample.class, "persons.firstname"))//
				.matches(e -> StringUtils.hasText(e.getMessage()))//
				.matches(e -> e.getResolvedPath().equals("persons"))//
				.matches(e -> e.getUnresolvableSegment().equals("firstname"))//
				.matches(e -> factory.from(PersonSample.class, e.getResolvedPath()) != null);
	}

	@Test // DATACMNS-1116
	public void cachesPersistentPropertyPaths() {

		assertThat(factory.from(PersonSample.class, "persons.name")) //
				.isSameAs(factory.from(PersonSample.class, "persons.name"));
	}

	@Test // DATACMNS-1275
	public void findsNestedPropertyByFilter() {

		Streamable<PersistentPropertyPath<SamplePersistentProperty>> path = factory
				.from(ClassTypeInformation.from(Sample.class), property -> property.findAnnotation(Inject.class) != null);

		assertThat(path).hasSize(1).anySatisfy(it -> it.toDotPath().equals("inner.annotatedField"));
	}

	@Test // DATACMNS-1275
	public void findsNestedPropertiesByFilter() {

		Streamable<PersistentPropertyPath<SamplePersistentProperty>> path = factory
				.from(ClassTypeInformation.from(Wrapper.class), property -> property.findAnnotation(Inject.class) != null);

		assertThat(path).hasSize(2);//
		assertThat(path).element(0).satisfies(it -> it.toDotPath().equals("first.inner.annotatedField"));
		assertThat(path).element(1).satisfies(it -> it.toDotPath().equals("second.inner.annotatedField"));
	}

	static class PersonSample {
		List<Person> persons;
	}

	class Person {
		String name;
	}

	static class Wrapper {
		Sample first;
		Sample second;
	}

	static class Sample {
		Inner inner;
	}

	static class Inner {
		@Inject String annotatedField;
		Sample cyclic;
	}

	static class Outer {
		Generic<Nested> field;
	}

	static class Generic<T> {
		T wrapped;
	}

	static class Nested {
		String field;
	}
}
