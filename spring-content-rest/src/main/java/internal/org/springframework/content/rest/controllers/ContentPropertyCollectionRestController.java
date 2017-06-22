package internal.org.springframework.content.rest.controllers;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Collections;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.content.commons.annotations.ContentId;
import org.springframework.content.commons.annotations.MimeType;
import org.springframework.content.commons.repository.ContentStore;
import org.springframework.content.commons.repository.Store;
import org.springframework.content.commons.storeservice.ContentStoreInfo;
import org.springframework.content.commons.storeservice.ContentStoreService;
import org.springframework.content.commons.utils.BeanUtils;
import org.springframework.content.rest.ResourceNotFoundException;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.repository.support.Repositories;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import internal.org.springframework.content.rest.annotations.ContentRestController;
import internal.org.springframework.content.rest.mappings.ContentHandlerMapping.StoreType;
import internal.org.springframework.content.rest.mappings.ContentRestByteRangeHttpRequestHandler;
import internal.org.springframework.content.rest.utils.ContentStoreUtils;
import internal.org.springframework.content.rest.utils.PersistentEntityUtils;

@ContentRestController
public class ContentPropertyCollectionRestController extends AbstractContentPropertyController {

	private static final String BASE_MAPPING = "/{repository}/{id}/{contentProperty}";

	private Repositories repositories;
	private ContentStoreService storeService;
	private ContentRestByteRangeHttpRequestHandler handler;
	
	@Autowired(required=false)
	public ContentPropertyCollectionRestController(ApplicationContext context, ContentStoreService stores, ContentRestByteRangeHttpRequestHandler handler) {
		this.repositories = new Repositories(context);
		this.storeService = stores;
		this.handler = handler;
	}
	
	@Autowired(required=false)
	public ContentPropertyCollectionRestController(Repositories repositories, ContentStoreService stores, ContentRestByteRangeHttpRequestHandler handler) {
		this.repositories = repositories;
		this.storeService = stores;
		this.handler = handler;
	}
	
	@StoreType("contentstore")
	@RequestMapping(value = BASE_MAPPING, method = RequestMethod.GET, headers = "accept!=application/hal+json")
	public void get(HttpServletRequest request, 
			   		HttpServletResponse response,
				    @PathVariable String repository, 
			  	    @PathVariable String id, 
			  	    @PathVariable String contentProperty) 
			throws HttpRequestMethodNotSupportedException {
		
		Object domainObj = findOne(repositories, repository, id);
				
		PersistentEntity<?,?> entity = repositories.getPersistentEntity(domainObj.getClass());
		if (null == entity)
			throw new ResourceNotFoundException();
		
		PersistentProperty<?> property = this.getContentPropertyDefinition(entity, contentProperty);
		if (PersistentEntityUtils.isPropertyMultiValued(property)) {
			response.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
			return;
		} 

		PersistentPropertyAccessor accessor = property.getOwner().getPropertyAccessor(domainObj);
		Object propVal = accessor.getProperty(property);
		if (propVal == null)
			throw new ResourceNotFoundException("No content");
		
		if (!BeanUtils.hasFieldWithAnnotation(propVal,ContentId.class)) {
			response.setStatus(HttpStatus.NOT_FOUND.value(), "Missing @ContentId");
			return;
		}
		
		Serializable cid = (Serializable) BeanUtils.getFieldWithAnnotation(propVal,ContentId.class);

		ContentStoreInfo info = ContentStoreUtils.findContentStore(storeService, propVal.getClass());
		if (info == null)
			throw new IllegalStateException(String.format("Unable to find a content store for %s", repository));
		
		org.springframework.core.io.Resource r = info.getImplementation(Store.class).getResource(cid);
		if (r == null) {
			response.setStatus(HttpStatus.NOT_FOUND.value());
			return;
		}

		request.setAttribute("SPRING_CONTENT_RESOURCE", r);

		if (BeanUtils.hasFieldWithAnnotation(propVal, MimeType.class)) {
			request.setAttribute("SPRING_CONTENT_CONTENTTYPE", BeanUtils.getFieldWithAnnotation(propVal, MimeType.class).toString());
		}

		try {
			handler.handleRequest(request, response);
		} catch (ServletException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return;
	}

	@StoreType("contentstore")
	@RequestMapping(value = BASE_MAPPING, method = RequestMethod.PUT, headers = "content-type!=multipart/form-data")
	@ResponseBody
	public ResponseEntity<Resource<?>> putContent(HttpServletRequest request,
						        				  HttpServletResponse response,
						        				  @PathVariable String repository, 
												  @PathVariable String id, 
												  @PathVariable String contentProperty) 
									throws IOException, HttpRequestMethodNotSupportedException, InstantiationException, IllegalAccessException {
		
		Object newContent = this.saveContentInternal(repositories, storeService, repository, id, contentProperty, request.getRequestURI(), request.getHeader("Content-Type"), request.getInputStream());
		if (newContent != null) {
			Resource<?> contentResource = toResource(request, newContent);
			return new ResponseEntity<Resource<?>>(contentResource, HttpStatus.CREATED);
		}
		return null;
	}

	@StoreType("contentstore")
	@RequestMapping(value = BASE_MAPPING, method = RequestMethod.POST, headers = "content-type!=multipart/form-data")
	@ResponseBody
	public ResponseEntity<Resource<?>> postContent(HttpServletRequest request,
			        							   HttpServletResponse response,
						        				   @PathVariable String repository, 
												   @PathVariable String id, 
												   @PathVariable String contentProperty) 
									throws IOException, HttpRequestMethodNotSupportedException, InstantiationException, IllegalAccessException {
		
		Object newContent = this.saveContentInternal(repositories, storeService, repository, id, contentProperty, request.getRequestURI(), request.getHeader("Content-Type"), request.getInputStream());
		if (newContent != null) {
			Resource<?> contentResource = toResource(request, newContent);
			return new ResponseEntity<Resource<?>>(contentResource, HttpStatus.CREATED);
		}
		return null;
	}

	@StoreType("contentstore")
	@RequestMapping(value = BASE_MAPPING, method = RequestMethod.POST, headers = "content-type=multipart/form-data")
	@ResponseBody
	public ResponseEntity<Resource<?>> postMultipartContent(HttpServletRequest request,
									 						HttpServletResponse response,
															@PathVariable String repository, 
															@PathVariable String id, 
															@PathVariable String contentProperty,
															@RequestParam("file") MultipartFile multiPart)
											 throws IOException, HttpRequestMethodNotSupportedException, InstantiationException, IllegalAccessException {

		Object newContent = this.saveContentInternal(repositories, storeService, repository, id, contentProperty, request.getRequestURI(), multiPart.getContentType(), multiPart.getInputStream());
		if (newContent != null) {
			Resource<?> contentResource = toResource(request, newContent);
			return new ResponseEntity<Resource<?>>(contentResource, HttpStatus.CREATED);
		}
		return null;
	}
	
	@StoreType("contentstore")
	@RequestMapping(value = BASE_MAPPING, method = RequestMethod.DELETE, headers = "accept!=application/hal+json")
	public void delete(HttpServletRequest request, 
					   HttpServletResponse response,
				       @PathVariable String repository, 
			  	       @PathVariable String id, 
			  	       @PathVariable String contentProperty) 
			throws HttpRequestMethodNotSupportedException {
		
		Object domainObj = findOne(repositories, repository, id);
				
		PersistentEntity<?,?> entity = repositories.getPersistentEntity(domainObj.getClass());
		if (null == entity) {
			response.setStatus(HttpStatus.NOT_FOUND.value());
			return;
		}
		
		PersistentProperty<?> property = this.getContentPropertyDefinition(entity, contentProperty);
		if (PersistentEntityUtils.isPropertyMultiValued(property)) {
			response.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
			return;
		} 

		PersistentPropertyAccessor accessor = property.getOwner().getPropertyAccessor(domainObj);
		Object propVal = accessor.getProperty(property);
		if (propVal == null) {
			response.setStatus(HttpStatus.NOT_FOUND.value());
			return;
		}
		
		if (!BeanUtils.hasFieldWithAnnotation(propVal,ContentId.class)) {
			response.setStatus(HttpStatus.NOT_FOUND.value(), "Missing @ContentId");
			return;
		}
		
		ContentStoreInfo info = ContentStoreUtils.findContentStore(storeService, propVal.getClass());
		if (info == null)
			throw new IllegalStateException(String.format("Unable to find a content store for %s", repository));
		
		info.getImplementation(ContentStore.class).unsetContent(propVal);
		
		if (BeanUtils.hasFieldWithAnnotation(propVal, MimeType.class)) {
			BeanUtils.setFieldWithAnnotation(propVal, MimeType.class, null);
		}

		save(repositories, repository, domainObj);
		
		response.setStatus(HttpStatus.NO_CONTENT.value());
		return;
	}

	Resource<?> toResource(final HttpServletRequest request, Object newContent)
			throws SecurityException, BeansException {
		Link self = new Link(StringUtils.trimTrailingCharacter(request.getRequestURL().toString(), '/') + "/" + BeanUtils.getFieldWithAnnotation(newContent, ContentId.class));
		Resource<?> contentResource = new Resource<Object>(newContent, Collections.singletonList(self));
		return contentResource;
	}	

	private Object saveContentInternal(Repositories repositories,
									   ContentStoreService stores,
									   String repository,
									   String id, 
									   String contentProperty,  
									   String requestUri,
									   String mimeType,
									   InputStream stream) 
			throws HttpRequestMethodNotSupportedException {
		
		Object domainObj = findOne(repositories, repository, id);
				
		PersistentEntity<?,?> entity = repositories.getPersistentEntity(domainObj.getClass());
		if (null == entity)
			throw new ResourceNotFoundException();
		
		PersistentProperty<?> property = this.getContentPropertyDefinition(entity, contentProperty);

		PersistentPropertyAccessor accessor = property.getOwner().getPropertyAccessor(domainObj);
		Object propVal = accessor.getProperty(property);
		Class<?> contentEntityClass = null;
		
		// null single-valued content property
		if (!PersistentEntityUtils.isPropertyMultiValued(property)) {
			contentEntityClass = property.getActualType();
		} 
		// null multi-valued content property
		else if (PersistentEntityUtils.isPropertyMultiValued(property)) {
			if (property.isArray()) {
				contentEntityClass = propVal.getClass().getComponentType();
			}
			else if (property.isCollectionLike()) {
				contentEntityClass = property.getActualType();
			}
		}
		
		ContentStoreInfo info = ContentStoreUtils.findContentStore(stores, contentEntityClass);
		if (info == null)
			throw new IllegalStateException(String.format("Unable to find a content store for %s", repository));

		// null single-valued content property
		if (propVal == null && !PersistentEntityUtils.isPropertyMultiValued(property)) {
			propVal = instantiate(info.getDomainObjectClass());
			accessor.setProperty(property, propVal);
		} 
		// null multi-valued content property
		else if (propVal == null && PersistentEntityUtils.isPropertyMultiValued(property)) {
			// TODO: instantiate an instance of the required arrays or collection/set/list and then 
			// an instance of the content property and add it to the list
		} 
		// non-null multi-valued property
		else if (propVal != null && PersistentEntityUtils.isPropertyMultiValued(property)) {

			// instantiate an instance of the member type and add it
			if (property.isArray()) {
				Class<?> memberType = propVal.getClass().getComponentType();
				Object member = instantiate(memberType);
				Object newArray = Array.newInstance(propVal.getClass(), Array.getLength(propVal) + 1);
				System.arraycopy(propVal, 0, newArray, 0, Array.getLength(propVal));
				Array.set(newArray, Array.getLength(propVal), member);
				accessor.setProperty(property, newArray);
				propVal = member;
				
			} else if (property.isCollectionLike()) {
				Class<?> memberType = property.getActualType();
				Object member = instantiate(memberType);
				@SuppressWarnings("unchecked") Collection<Object> contentCollection = (Collection<Object>)accessor.getProperty(property);
				contentCollection.add(member);
				propVal = member;
			}
		}

		if (BeanUtils.hasFieldWithAnnotation(propVal, MimeType.class)) {
			BeanUtils.setFieldWithAnnotation(propVal, MimeType.class, mimeType);
		}
		
		info.getImpementation().setContent(propVal, stream);
		
		save(repositories, repository, domainObj);
		
		return propVal;
	}
	
	private Object instantiate(Class<?> clazz) {
		Object newObject = null;
		try {
			newObject = clazz.newInstance();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		return newObject;
	}
}