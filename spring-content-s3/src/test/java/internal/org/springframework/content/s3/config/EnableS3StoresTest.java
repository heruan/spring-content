package internal.org.springframework.content.s3.config;

import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.aws.core.io.s3.SimpleStorageResourceLoader;
import org.springframework.content.commons.annotations.Content;
import org.springframework.content.commons.annotations.ContentId;
import org.springframework.content.commons.repository.ContentStore;
import org.springframework.content.s3.config.EnableS3ContentRepositories;
import org.springframework.content.s3.config.EnableS3Stores;
import org.springframework.content.s3.config.S3StoreConfigurer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.github.paulcwarren.ginkgo4j.Ginkgo4jRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.AfterEach;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.BeforeEach;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.Context;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.Describe;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.It;

@RunWith(Ginkgo4jRunner.class)
public class EnableS3StoresTest {

	private AnnotationConfigApplicationContext context;

	// mocks
	static S3StoreConfigurer configurer;
	{
		Describe("EnableS3Stores", () -> {
			Context("given a context and a configuartion with an S3 content repository bean", () -> {
				BeforeEach(() -> {
					context = new AnnotationConfigApplicationContext();
					context.register(TestConfig.class);
					context.refresh();
				});
				AfterEach(() -> {
					context.close();
				});
				It("should have a Content Repository bean", () -> {
					assertThat(context.getBean(TestEntityContentRepository.class), is(not(nullValue())));
				});
				It("should have an s3 store converter", () -> {
					assertThat(context.getBean("s3StoreConverter"), is(not(nullValue())));
				});
			});
			
			Context("given a context with a configurer", () -> {
				BeforeEach(() -> {
					configurer = mock(S3StoreConfigurer.class);
					
					context = new AnnotationConfigApplicationContext();
					context.register(ConverterConfig.class);
					context.refresh();
				});
				AfterEach(() -> {
					context.close();
				});
				It("should call that configurer to help setup the store", () -> {
					verify(configurer).configureS3StoreConverters(anyObject());
				});
			});

			Context("given a context with an empty configuration", () -> {
				BeforeEach(() -> {
					context = new AnnotationConfigApplicationContext();
					context.register(EmptyConfig.class);
					context.refresh();
				});
				AfterEach(() -> {
					context.close();
				});
				It("should not contains any S3 repository beans", () -> {
					try {
						context.getBean(TestEntityContentRepository.class);
						fail("expected no such bean");
					} catch (NoSuchBeanDefinitionException e) {
						assertThat(true, is(true));
					}
				});
			});
		});
		
		Describe("EnableS3ContentRepositories", () -> {
			Context("given a context and a configuartion with an S3 content repository bean", () -> {
				BeforeEach(() -> {
					context = new AnnotationConfigApplicationContext();
					context.register(EnableS3ContentRepositoriesConfig.class);
					context.refresh();
				});
				AfterEach(() -> {
					context.close();
				});
				It("should have a Content Repository bean", () -> {
					assertThat(context.getBean(TestEntityContentRepository.class), is(not(nullValue())));
				});
				It("should have an s3 store converter", () -> {
					assertThat(context.getBean("s3StoreConverter"), is(not(nullValue())));
				});
			});
		});
	}


	@Test
	public void noop() {
	}

	@Configuration
	@EnableS3Stores(basePackages="contains.no.fs.repositores")
//	@EnableContextResourceLoader
	@Import(InfrastructureConfig.class)
	public static class EmptyConfig {
	}

	@Configuration
	@EnableS3Stores
//	@EnableContextResourceLoader
	@Import(InfrastructureConfig.class)
	public static class TestConfig {
	}
	
	@Configuration
	@EnableS3Stores
//	@EnableContextResourceLoader
	@Import(InfrastructureConfig.class)
	public static class ConverterConfig {
		@Bean
		public S3StoreConfigurer configurer() {
			return configurer;
		}
	}

	@Configuration
	@EnableS3ContentRepositories
//	@EnableContextResourceLoader
	@Import(InfrastructureConfig.class)
	public static class EnableS3ContentRepositoriesConfig {
	}
	
	@Configuration
	public static class InfrastructureConfig {

		public Region region() {
			return Region.getRegion(Regions.US_WEST_1);
		}
		
		@Bean
		public AmazonS3 client() {
			AmazonS3Client client = new AmazonS3Client();
			client.setRegion(region());
			return client;
		}

		@Bean
		public SimpleStorageResourceLoader simpleStorageResourceLoader(AmazonS3 client) {
			return new SimpleStorageResourceLoader(client);
		}
	}

	@Content
	public class TestEntity {
		@ContentId
		private String contentId;
	}

	public interface TestEntityContentRepository extends ContentStore<TestEntity, String> {
	}
}
