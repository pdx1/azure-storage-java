/**
 * Copyright Microsoft Corporation
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.microsoft.azure.storage.blob;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;

import javax.xml.stream.XMLStreamException;

import com.microsoft.azure.storage.AccessCondition;
import com.microsoft.azure.storage.Constants;
import com.microsoft.azure.storage.DoesServiceRequest;
import com.microsoft.azure.storage.OperationContext;
import com.microsoft.azure.storage.ResultContinuation;
import com.microsoft.azure.storage.ResultContinuationType;
import com.microsoft.azure.storage.ResultSegment;
import com.microsoft.azure.storage.SharedAccessPolicyHandler;
import com.microsoft.azure.storage.SharedAccessPolicySerializer;
import com.microsoft.azure.storage.StorageCredentialsSharedAccessSignature;
import com.microsoft.azure.storage.StorageErrorCode;
import com.microsoft.azure.storage.StorageErrorCodeStrings;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.StorageUri;
import com.microsoft.azure.storage.core.ExecutionEngine;
import com.microsoft.azure.storage.core.LazySegmentedIterable;
import com.microsoft.azure.storage.core.PathUtility;
import com.microsoft.azure.storage.core.RequestLocationMode;
import com.microsoft.azure.storage.core.SR;
import com.microsoft.azure.storage.core.SegmentedStorageRequest;
import com.microsoft.azure.storage.core.SharedAccessSignatureHelper;
import com.microsoft.azure.storage.core.StorageCredentialsHelper;
import com.microsoft.azure.storage.core.StorageRequest;
import com.microsoft.azure.storage.core.UriQueryBuilder;
import com.microsoft.azure.storage.core.Utility;

/**
 * Represents a container in the Microsoft Azure Blob service.
 * <p>
 * Containers hold directories, which are encapsulated as {@link CloudBlobDirectory} objects, and directories hold block
 * blobs and page blobs. Directories can also contain sub-directories.
 */
public final class CloudBlobContainer {

    /**
     * Converts the ACL string to a BlobContainerPermissions object.
     * 
     * @param aclString
     *            A <code>String</code> which specifies the ACLs to convert.
     * 
     * @return A {@link BlobContainerPermissions} object which represents the ACLs.
     */
    static BlobContainerPermissions getContainerAcl(final String aclString) {
        BlobContainerPublicAccessType accessType = BlobContainerPublicAccessType.OFF;

        if (!Utility.isNullOrEmpty(aclString)) {
            final String lowerAclString = aclString.toLowerCase();
            if ("container".equals(lowerAclString)) {
                accessType = BlobContainerPublicAccessType.CONTAINER;
            }
            else if ("blob".equals(lowerAclString)) {
                accessType = BlobContainerPublicAccessType.BLOB;
            }
            else {
                throw new IllegalArgumentException(String.format(SR.INVALID_ACL_ACCESS_TYPE, aclString));
            }
        }

        final BlobContainerPermissions retVal = new BlobContainerPermissions();
        retVal.setPublicAccess(accessType);

        return retVal;
    }

    /**
     * Represents the container metadata.
     */
    protected HashMap<String, String> metadata;

    /**
     * Holds the container properties.
     */
    BlobContainerProperties properties;

    /**
     * Holds the name of the container.
     */
    String name;

    /**
     * Holds the list of URIs for all locations.
     */
    private StorageUri storageUri;

    /**
     * Holds a reference to the associated service client.
     */
    private CloudBlobClient blobServiceClient;

    /**
     * Initializes a new instance of the CloudBlobContainer class.
     * 
     * @param client
     *            A {@link CloudBlobClient} which represents a reference to the associated service client.
     */
    private CloudBlobContainer(final CloudBlobClient client) {
        this.metadata = new HashMap<String, String>();
        this.properties = new BlobContainerProperties();
        this.blobServiceClient = client;
    }

    /**
     * Creates an instance of the <code>CloudBlobContainer</code> class using the specified URI. The blob URI should
     * include a SAS token unless anonymous access is to be used.
     * 
     * @param uri
     *            A <code>java.net.URI</code> object which represents the URI of the container.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     * @throws URISyntaxException
     *             If the resource URI is invalid.
     */
    public CloudBlobContainer(final URI uri) throws URISyntaxException, StorageException {
        this(new StorageUri(uri));
    }

    /**
     * Creates an instance of the <code>CloudBlobContainer</code> class using the specified URI. The blob URI should
     * include a SAS token unless anonymous access is to be used.
     * 
     * @param storageUri
     *            A {@link StorageUri} object which represents the URI of the container.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     * @throws URISyntaxException
     *             If the resource URI is invalid.
     */
    public CloudBlobContainer(final StorageUri storageUri) throws URISyntaxException, StorageException {
        this(storageUri, (CloudBlobClient) null /* client */);
    }

    /**
     * Creates an instance of the <code>CloudBlobContainer</code> class using the specified name and client.
     * 
     * @param containerName
     *            A <code>String</code> which represents the name of the container, which must adhere to container
     *            naming rules.
     *            The container name should not include any path separator characters (/).
     *            Container names must be lowercase, between 3-63 characters long and must start with a letter or
     *            number. Container names may contain only letters, numbers, and the dash (-) character.
     * @param client
     *            A {@link CloudBlobClient} object that represents the associated service client, and that specifies the
     *            endpoint for the Blob service. *
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     * @throws URISyntaxException
     *             If the resource URI constructed based on the containerName is invalid.
     * 
     * @see <a href="http://msdn.microsoft.com/library/azure/dd135715.aspx">Naming and Referencing Containers, Blobs,
     *      and Metadata</a>
     */
    public CloudBlobContainer(final String containerName, final CloudBlobClient client) throws URISyntaxException,
            StorageException {
        this(client);
        Utility.assertNotNull("client", client);
        Utility.assertNotNull("containerName", containerName);

        this.storageUri = PathUtility.appendPathToUri(client.getStorageUri(), containerName);

        this.name = containerName;
        this.parseQueryAndVerify(this.storageUri, client, client.isUsePathStyleUris());
    }

    /**
     * Creates an instance of the <code>CloudBlobContainer</code> class using the specified URI and client.
     * 
     * @param uri
     *            A <code>java.net.URI</code> object that represents the absolute URI of the container.
     * @param client
     *            A {@link CloudBlobClient} object that represents the associated service client, and that specifies the
     *            endpoint for the Blob service.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     * @throws URISyntaxException
     *             If the resource URI is invalid.
     */
    public CloudBlobContainer(final URI uri, final CloudBlobClient client) throws URISyntaxException, StorageException {
        this(new StorageUri(uri), client);
    }

    /**
     * Creates an instance of the <code>CloudBlobContainer</code> class using the specified URI and client.
     * 
     * @param storageUri
     *            A {@link StorageUri} object which represents the absolute URI of the container.
     * @param client
     *            A {@link CloudBlobClient} object that represents the associated service client, and that specifies the
     *            endpoint for the Blob service.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     * @throws URISyntaxException
     *             If the resource URI is invalid.
     */
    public CloudBlobContainer(final StorageUri storageUri, final CloudBlobClient client) throws URISyntaxException,
            StorageException {
        this(client);

        Utility.assertNotNull("storageUri", storageUri);

        this.storageUri = storageUri;

        boolean usePathStyleUris = client == null ? Utility.determinePathStyleFromUri(this.storageUri.getPrimaryUri())
                : client.isUsePathStyleUris();

        this.name = PathUtility.getContainerNameFromUri(storageUri.getPrimaryUri(), usePathStyleUris);

        this.parseQueryAndVerify(this.storageUri, client, usePathStyleUris);
    }

    /**
     * Creates the container.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public void create() throws StorageException {
        this.create(null /* options */, null /* opContext */);
    }

    /**
     * Creates the container using the specified options and operation context.
     * 
     * @param options
     *            A {@link BlobRequestOptions} object that specifies any additional options for the request. Specifying
     *            <code>null</code> will use the default request options from the associated service client (
     *            {@link CloudBlobClient}).
     * @param opContext
     *            An {@link OperationContext} object that represents the context for the current operation. This object
     *            is used to track requests to the storage service, and to provide additional runtime information about
     *            the operation.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public void create(BlobRequestOptions options, OperationContext opContext) throws StorageException {
        if (opContext == null) {
            opContext = new OperationContext();
        }

        opContext.initialize();
        options = BlobRequestOptions.applyDefaults(options, BlobType.UNSPECIFIED, this.blobServiceClient);

        ExecutionEngine.executeWithRetry(this.blobServiceClient, this, createImpl(options),
                options.getRetryPolicyFactory(), opContext);

    }

    private StorageRequest<CloudBlobClient, CloudBlobContainer, Void> createImpl(final BlobRequestOptions options) {
        final StorageRequest<CloudBlobClient, CloudBlobContainer, Void> putRequest = new StorageRequest<CloudBlobClient, CloudBlobContainer, Void>(
                options, this.getStorageUri()) {
            @Override
            public HttpURLConnection buildRequest(CloudBlobClient client, CloudBlobContainer container,
                    OperationContext context) throws Exception {
                final HttpURLConnection request = BlobRequest.createContainer(
                        container.getStorageUri().getUri(this.getCurrentLocation()), options, context);
                return request;
            }

            @Override
            public void setHeaders(HttpURLConnection connection, CloudBlobContainer container, OperationContext context) {
                BlobRequest.addMetadata(connection, container.metadata, context);
            }

            @Override
            public void signRequest(HttpURLConnection connection, CloudBlobClient client, OperationContext context)
                    throws Exception {
                StorageRequest.signBlobAndQueueRequest(connection, client, 0L, null);
            }

            @Override
            public Void preProcessResponse(CloudBlobContainer container, CloudBlobClient client,
                    OperationContext context) throws Exception {
                if (this.getResult().getStatusCode() != HttpURLConnection.HTTP_CREATED) {
                    this.setNonExceptionedRetryableFailure(true);
                    return null;
                }

                // Set attributes
                final BlobContainerAttributes attributes = BlobResponse.getBlobContainerAttributes(
                        this.getConnection(), client.isUsePathStyleUris());
                container.properties = attributes.getProperties();
                container.name = attributes.getName();
                return null;
            }
        };

        return putRequest;
    }

    /**
     * Creates the container if it does not exist.
     * 
     * @return <code>true</code> if the container did not already exist and was created; otherwise, <code>false</code>.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public boolean createIfNotExists() throws StorageException {
        return this.createIfNotExists(null /* options */, null /* opContext */);
    }

    /**
     * Creates the container if it does not exist, using the specified request options and operation context.
     * 
     * @param options
     *            A {@link BlobRequestOptions} object that specifies any additional options for the request.
     *            Specifying <code>null</code> will use the default request options from the associated service client
     *            ({@link CloudBlobClient}).
     * @param opContext
     *            An {@link OperationContext} object that represents the context for the current operation. This object
     *            is used to track requests to the storage service, and to provide additional runtime information about
     *            the operation.
     * 
     * @return <code>true</code> if the container did not already exist and was created; otherwise, <code>false</code>.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public boolean createIfNotExists(BlobRequestOptions options, OperationContext opContext) throws StorageException {
        options = BlobRequestOptions.applyDefaults(options, BlobType.UNSPECIFIED, this.blobServiceClient);

        boolean exists = this.exists(true /* primaryOnly */, null /* accessCondition */, options, opContext);
        if (exists) {
            return false;
        }
        else {
            try {
                this.create(options, opContext);
                return true;
            }
            catch (StorageException e) {
                if (e.getHttpStatusCode() == HttpURLConnection.HTTP_CONFLICT
                        && StorageErrorCodeStrings.CONTAINER_ALREADY_EXISTS.equals(e.getErrorCode())) {
                    return false;
                }
                else {
                    throw e;
                }
            }
        }
    }

    /**
     * Deletes the container.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public void delete() throws StorageException {
        this.delete(null /* accessCondition */, null /* options */, null /* opContext */);
    }

    /**
     * Deletes the container using the specified request options and operation context.
     * 
     * @param accessCondition
     *            An {@link AccessCondition} object that represents the access conditions for the container.
     * @param options
     *            A {@link BlobRequestOptions} object that specifies any additional options for the request. Specifying
     *            <code>null</code> will use the default request options from the associated service client (
     *            {@link CloudBlobClient}).
     * @param opContext
     *            An {@link OperationContext} object that represents the context for the current operation. This object
     *            is used to track requests to the storage service, and to provide additional runtime information about
     *            the operation.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public void delete(AccessCondition accessCondition, BlobRequestOptions options, OperationContext opContext)
            throws StorageException {
        if (opContext == null) {
            opContext = new OperationContext();
        }

        opContext.initialize();
        options = BlobRequestOptions.applyDefaults(options, BlobType.UNSPECIFIED, this.blobServiceClient);

        ExecutionEngine.executeWithRetry(this.blobServiceClient, this, deleteImpl(accessCondition, options),
                options.getRetryPolicyFactory(), opContext);
    }

    private StorageRequest<CloudBlobClient, CloudBlobContainer, Void> deleteImpl(final AccessCondition accessCondition,
            final BlobRequestOptions options) {
        final StorageRequest<CloudBlobClient, CloudBlobContainer, Void> putRequest = new StorageRequest<CloudBlobClient, CloudBlobContainer, Void>(
                options, this.getStorageUri()) {
            @Override
            public HttpURLConnection buildRequest(CloudBlobClient client, CloudBlobContainer container,
                    OperationContext context) throws Exception {
                return BlobRequest.deleteContainer(container.getStorageUri().getPrimaryUri(), options, context,
                        accessCondition);
            }

            @Override
            public void signRequest(HttpURLConnection connection, CloudBlobClient client, OperationContext context)
                    throws Exception {
                StorageRequest.signBlobAndQueueRequest(connection, client, -1L, null);
            }

            @Override
            public Void preProcessResponse(CloudBlobContainer container, CloudBlobClient client,
                    OperationContext context) throws Exception {
                if (this.getResult().getStatusCode() != HttpURLConnection.HTTP_ACCEPTED) {
                    this.setNonExceptionedRetryableFailure(true);
                }

                return null;
            }
        };

        return putRequest;
    }

    /**
     * Deletes the container if it exists.
     * 
     * @return <code>true</code> if the container did not already exist and was created; otherwise, <code>false</code>.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public boolean deleteIfExists() throws StorageException {
        return this.deleteIfExists(null /* accessCondition */, null /* options */, null /* opContext */);
    }

    /**
     * Deletes the container if it exists using the specified request options and operation context.
     * 
     * @param accessCondition
     *            An {@link AccessCondition} object that represents the access conditions for the container.
     * @param options
     *            A {@link BlobRequestOptions} object that specifies any additional options for the request. Specifying
     *            <code>null</code> will use the default request options from the associated service client (
     *            {@link CloudBlobClient}).
     * @param opContext
     *            An {@link OperationContext} object that represents the context for the current operation. This object
     *            is used to track requests to the storage service, and to provide additional runtime information about
     *            the operation.
     * 
     * @return <code>true</code> if the container existed and was deleted; otherwise, <code>false</code>.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public boolean deleteIfExists(AccessCondition accessCondition, BlobRequestOptions options,
            OperationContext opContext) throws StorageException {
        options = BlobRequestOptions.applyDefaults(options, BlobType.UNSPECIFIED, this.blobServiceClient);

        boolean exists = this.exists(true /* primaryOnly */, accessCondition, options, opContext);
        if (exists) {
            try {
                this.delete(accessCondition, options, opContext);
                return true;
            }
            catch (StorageException e) {
                if (e.getHttpStatusCode() == HttpURLConnection.HTTP_NOT_FOUND
                        && StorageErrorCode.RESOURCE_NOT_FOUND.toString().equals(e.getErrorCode())) {
                    return false;
                }
                else {
                    throw e;
                }
            }
        }
        else {
            return false;
        }
    }

    /**
     * Downloads the container's attributes, which consist of metadata and properties.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public void downloadAttributes() throws StorageException {
        this.downloadAttributes(null /* accessCondition */, null /* options */, null /* opContext */);
    }

    /**
     * Downloads the container's attributes, which consist of metadata and properties, using the specified request
     * options and operation context.
     * 
     * @param accessCondition
     *            An {@link AccessCondition} object that represents the access conditions for the container.
     * @param options
     *            A {@link BlobRequestOptions} object that specifies any additional options for the request. Specifying
     *            <code>null</code> will use the default request options from the associated service client (
     *            {@link CloudBlobClient}).
     * @param opContext
     *            An {@link OperationContext} object that represents the context for the current operation. This object
     *            is used to track requests to the storage service, and to provide additional runtime information about
     *            the operation.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public void downloadAttributes(AccessCondition accessCondition, BlobRequestOptions options,
            OperationContext opContext) throws StorageException {
        if (opContext == null) {
            opContext = new OperationContext();
        }

        opContext.initialize();
        options = BlobRequestOptions.applyDefaults(options, BlobType.UNSPECIFIED, this.blobServiceClient);

        ExecutionEngine.executeWithRetry(this.blobServiceClient, this,
                this.downloadAttributesImpl(accessCondition, options), options.getRetryPolicyFactory(), opContext);
    }

    private StorageRequest<CloudBlobClient, CloudBlobContainer, Void> downloadAttributesImpl(
            final AccessCondition accessCondition, final BlobRequestOptions options) {
        final StorageRequest<CloudBlobClient, CloudBlobContainer, Void> getRequest = new StorageRequest<CloudBlobClient, CloudBlobContainer, Void>(
                options, this.getStorageUri()) {

            @Override
            public void setRequestLocationMode() {
                this.setRequestLocationMode(RequestLocationMode.PRIMARY_OR_SECONDARY);
            }

            @Override
            public HttpURLConnection buildRequest(CloudBlobClient client, CloudBlobContainer container,
                    OperationContext context) throws Exception {
                return BlobRequest.getContainerProperties(container.getStorageUri().getUri(this.getCurrentLocation()),
                        options, context, accessCondition);
            }

            @Override
            public void signRequest(HttpURLConnection connection, CloudBlobClient client, OperationContext context)
                    throws Exception {
                StorageRequest.signBlobAndQueueRequest(connection, client, -1L, null);
            }

            @Override
            public Void preProcessResponse(CloudBlobContainer container, CloudBlobClient client,
                    OperationContext context) throws Exception {
                if (this.getResult().getStatusCode() != HttpURLConnection.HTTP_OK) {
                    this.setNonExceptionedRetryableFailure(true);
                    return null;
                }

                // Set attributes
                final BlobContainerAttributes attributes = BlobResponse.getBlobContainerAttributes(
                        this.getConnection(), client.isUsePathStyleUris());
                container.metadata = attributes.getMetadata();
                container.properties = attributes.getProperties();
                container.name = attributes.getName();
                return null;
            }
        };

        return getRequest;
    }

    /**
     * Downloads the permission settings for the container.
     * 
     * @return A {@link BlobContainerPermissions} object that represents the container's permissions.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public BlobContainerPermissions downloadPermissions() throws StorageException {
        return this.downloadPermissions(null /* accessCondition */, null /* options */, null /* opContext */);
    }

    /**
     * Downloads the permissions settings for the container using the specified request options and operation context.
     * 
     * @param accessCondition
     *            An {@link AccessCondition} object that represents the access conditions for the container.
     * @param options
     *            A {@link BlobRequestOptions} object that specifies any additional options for the request. Specifying
     *            <code>null</code> will use the default request options from the associated service client (
     *            {@link CloudBlobClient}).
     * @param opContext
     *            An {@link OperationContext} object that represents the context for the current operation. This object
     *            is used to track requests to the storage service, and to provide additional runtime information about
     *            the operation.
     * 
     * @return A {@link BlobContainerPermissions} object that represents the container's permissions.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public BlobContainerPermissions downloadPermissions(AccessCondition accessCondition, BlobRequestOptions options,
            OperationContext opContext) throws StorageException {
        if (opContext == null) {
            opContext = new OperationContext();
        }

        opContext.initialize();
        options = BlobRequestOptions.applyDefaults(options, BlobType.UNSPECIFIED, this.blobServiceClient);

        return ExecutionEngine.executeWithRetry(this.blobServiceClient, this,
                downloadPermissionsImpl(accessCondition, options), options.getRetryPolicyFactory(), opContext);
    }

    private StorageRequest<CloudBlobClient, CloudBlobContainer, BlobContainerPermissions> downloadPermissionsImpl(
            final AccessCondition accessCondition, final BlobRequestOptions options) {
        final StorageRequest<CloudBlobClient, CloudBlobContainer, BlobContainerPermissions> getRequest = new StorageRequest<CloudBlobClient, CloudBlobContainer, BlobContainerPermissions>(
                options, this.getStorageUri()) {

            @Override
            public void setRequestLocationMode() {
                this.setRequestLocationMode(RequestLocationMode.PRIMARY_OR_SECONDARY);
            }

            @Override
            public HttpURLConnection buildRequest(CloudBlobClient client, CloudBlobContainer container,
                    OperationContext context) throws Exception {
                return BlobRequest.getAcl(container.getStorageUri().getUri(this.getCurrentLocation()), options,
                        accessCondition, context);
            }

            @Override
            public void signRequest(HttpURLConnection connection, CloudBlobClient client, OperationContext context)
                    throws Exception {
                StorageRequest.signBlobAndQueueRequest(connection, client, -1L, null);
            }

            @Override
            public BlobContainerPermissions preProcessResponse(CloudBlobContainer container, CloudBlobClient client,
                    OperationContext context) throws Exception {
                if (this.getResult().getStatusCode() != HttpURLConnection.HTTP_OK) {
                    this.setNonExceptionedRetryableFailure(true);
                }

                container.updatePropertiesFromResponse(this.getConnection());
                final String aclString = BlobResponse.getAcl(this.getConnection());
                final BlobContainerPermissions containerAcl = getContainerAcl(aclString);
                return containerAcl;
            }

            @Override
            public BlobContainerPermissions postProcessResponse(HttpURLConnection connection,
                    CloudBlobContainer container, CloudBlobClient client, OperationContext context,
                    BlobContainerPermissions containerAcl) throws Exception {
                HashMap<String, SharedAccessBlobPolicy> accessIds = SharedAccessPolicyHandler.getAccessIdentifiers(this
                        .getConnection().getInputStream(), SharedAccessBlobPolicy.class);

                for (final String key : accessIds.keySet()) {
                    containerAcl.getSharedAccessPolicies().put(key, accessIds.get(key));
                }

                return containerAcl;
            }
        };

        return getRequest;
    }

    /**
     * Returns a value that indicates whether the container exists.
     * 
     * @return <code>true</code> if the container exists, otherwise <code>false</code>.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public boolean exists() throws StorageException {
        return this.exists(null /* accessCondition */, null /* options */, null /* opContext */);
    }

    /**
     * Returns a value that indicates whether the container exists, using the specified request options and operation
     * context.
     * 
     * @param accessCondition
     *            An {@link AccessCondition} object that represents the access conditions for the container.
     * @param options
     *            A {@link BlobRequestOptions} object that specifies any additional options for the request. Specifying
     *            <code>null</code> will use the default request options from the associated service client (
     *            {@link CloudBlobClient}).
     * @param opContext
     *            An {@link OperationContext} object that represents the context for the current operation. This object
     *            is used to track requests to the storage service, and to provide additional runtime information about
     *            the operation.
     * 
     * @return <code>true</code> if the container exists, otherwise <code>false</code>.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public boolean exists(final AccessCondition accessCondition, BlobRequestOptions options, OperationContext opContext)
            throws StorageException {
        return this.exists(false, accessCondition, options, opContext);
    }

    @DoesServiceRequest
    private boolean exists(final boolean primaryOnly, final AccessCondition accessCondition,
            BlobRequestOptions options, OperationContext opContext) throws StorageException {
        if (opContext == null) {
            opContext = new OperationContext();
        }

        opContext.initialize();
        options = BlobRequestOptions.applyDefaults(options, BlobType.UNSPECIFIED, this.blobServiceClient);

        return ExecutionEngine.executeWithRetry(this.blobServiceClient, this,
                this.existsImpl(primaryOnly, accessCondition, options), options.getRetryPolicyFactory(), opContext);
    }

    private StorageRequest<CloudBlobClient, CloudBlobContainer, Boolean> existsImpl(final boolean primaryOnly,
            final AccessCondition accessCondition, final BlobRequestOptions options) {
        final StorageRequest<CloudBlobClient, CloudBlobContainer, Boolean> getRequest = new StorageRequest<CloudBlobClient, CloudBlobContainer, Boolean>(
                options, this.getStorageUri()) {

            @Override
            public void setRequestLocationMode() {
                this.setRequestLocationMode(primaryOnly ? RequestLocationMode.PRIMARY_ONLY
                        : RequestLocationMode.PRIMARY_OR_SECONDARY);
            }

            @Override
            public HttpURLConnection buildRequest(CloudBlobClient client, CloudBlobContainer container,
                    OperationContext context) throws Exception {
                return BlobRequest.getContainerProperties(container.getStorageUri().getUri(this.getCurrentLocation()),
                        options, context, accessCondition);
            }

            @Override
            public void signRequest(HttpURLConnection connection, CloudBlobClient client, OperationContext context)
                    throws Exception {
                StorageRequest.signBlobAndQueueRequest(connection, client, -1L, null);
            }

            @Override
            public Boolean preProcessResponse(CloudBlobContainer container, CloudBlobClient client,
                    OperationContext context) throws Exception {
                if (this.getResult().getStatusCode() == HttpURLConnection.HTTP_OK) {
                    container.updatePropertiesFromResponse(this.getConnection());
                    return Boolean.valueOf(true);
                }
                else if (this.getResult().getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                    return Boolean.valueOf(false);
                }
                else {
                    this.setNonExceptionedRetryableFailure(true);
                    // return false instead of null to avoid SCA issues
                    return false;
                }
            }
        };

        return getRequest;
    }

    /**
     * Returns a shared access signature for the container. Note this does not contain the leading "?".
     * 
     * @param policy
     *            An {@link SharedAccessBlobPolicy} object that represents the access policy for the shared access
     *            signature.
     * @param groupPolicyIdentifier
     *            A <code>String</code> which represents the container-level access policy.
     * 
     * @return A <code>String</code> which represents a shared access signature for the container.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     * @throws InvalidKeyException
     *             If the key is invalid.
     */
    public String generateSharedAccessSignature(final SharedAccessBlobPolicy policy, final String groupPolicyIdentifier)
            throws InvalidKeyException, StorageException {

        if (!StorageCredentialsHelper.canCredentialsSignRequest(this.blobServiceClient.getCredentials())) {
            final String errorMessage = SR.CANNOT_CREATE_SAS_WITHOUT_ACCOUNT_KEY;
            throw new IllegalArgumentException(errorMessage);
        }

        final String resourceName = this.getSharedAccessCanonicalName();

        final String signature = SharedAccessSignatureHelper.generateSharedAccessSignatureHashForBlob(policy,
                null /* SharedAccessBlobHeaders */, groupPolicyIdentifier, resourceName, this.blobServiceClient, null);

        final UriQueryBuilder builder = SharedAccessSignatureHelper.generateSharedAccessSignatureForBlob(policy,
                null /* SharedAccessBlobHeaders */, groupPolicyIdentifier, "c", signature);

        return builder.toString();
    }

    /**
     * Returns a reference to a {@link CloudBlockBlob} object that represents a block blob in this container.
     * 
     * @param blobName
     *            A <code>String</code> that represents the name of the blob.
     * 
     * @return A {@link CloudBlockBlob} object that represents a reference to the specified block blob.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     * @throws URISyntaxException
     *             If the resource URI is invalid.
     */
    public CloudBlockBlob getBlockBlobReference(final String blobName) throws URISyntaxException, StorageException {
        Utility.assertNotNullOrEmpty("blobName", blobName);

        final StorageUri address = PathUtility.appendPathToUri(this.storageUri, blobName);

        return new CloudBlockBlob(address, this.blobServiceClient, this);
    }

    /**
     * Returns a reference to a {@link CloudBlockBlob} object that represents a block blob in this container, using the
     * specified snapshot ID.
     * 
     * @param blobName
     *            A <code>String</code> that represents the name of the blob.
     * @param snapshotID
     *            A <code>String</code> that represents the snapshot ID of the blob.
     * 
     * @return A {@link CloudBlockBlob} object that represents a reference to the specified block blob.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     * @throws URISyntaxException
     *             If the resource URI is invalid.
     */
    public CloudBlockBlob getBlockBlobReference(final String blobName, final String snapshotID)
            throws URISyntaxException, StorageException {
        Utility.assertNotNullOrEmpty("blobName", blobName);

        final StorageUri address = PathUtility.appendPathToUri(this.storageUri, blobName);

        final CloudBlockBlob retBlob = new CloudBlockBlob(address, snapshotID, this.blobServiceClient);
        retBlob.setContainer(this);
        return retBlob;
    }

    /**
     * Returns a reference to a {@link CloudBlobDirectory} object that represents a virtual blob directory within this
     * container.
     * 
     * @param directoryName
     *            A <code>String</code> that represents the name of the virtual blob directory. If the root directory
     *            (the directory representing the container itself) is desired, use an empty string.
     * @return A {@link CloudBlobDirectory} that represents a virtual blob directory within this container.
     * @throws URISyntaxException
     *             If the resource URI is invalid.
     */
    public CloudBlobDirectory getDirectoryReference(String directoryName) throws URISyntaxException {
        Utility.assertNotNull("directoryName", directoryName);

        // if the directory name does not end in the delimiter, add the delimiter
        if (!directoryName.isEmpty() && !directoryName.endsWith(this.blobServiceClient.getDirectoryDelimiter())) {
            directoryName = directoryName.concat(this.blobServiceClient.getDirectoryDelimiter());
        }

        final StorageUri address = PathUtility.appendPathToUri(this.storageUri, directoryName);

        return new CloudBlobDirectory(address, directoryName, this.blobServiceClient, this);
    }

    /**
     * Returns the metadata for the container. This value is initialized with the metadata from the queue by a call to
     * {@link #downloadAttributes}, and is set on the queue with a call to {@link #uploadMetadata}.
     * 
     * @return A <code>java.util.HashMap</code> object that represents the metadata for the container.
     */
    public HashMap<String, String> getMetadata() {
        return this.metadata;
    }

    /**
     * Returns the name of the container.
     * 
     * @return A <code>String</code> that represents the name of the container.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Returns the list of URIs for all locations.
     * 
     * @return A {@link StorageUri} object which represents the list of URIs for all locations..
     */
    public StorageUri getStorageUri() {
        return this.storageUri;
    }

    /**
     * Returns a reference to a {@link CloudPageBlob} object that represents a page blob in this container.
     * 
     * @param blobName
     *            A <code>String</code> that represents the name of the blob.
     * 
     * @return A {@link CloudPageBlob} object that represents a reference to the specified page blob.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     * @throws URISyntaxException
     *             If the resource URI is invalid.
     */
    public CloudPageBlob getPageBlobReference(final String blobName) throws URISyntaxException, StorageException {
        Utility.assertNotNullOrEmpty("blobName", blobName);

        final StorageUri address = PathUtility.appendPathToUri(this.storageUri, blobName);

        return new CloudPageBlob(address, this.blobServiceClient, this);
    }

    /**
     * Returns a reference to a {@link CloudPageBlob} object that represents a page blob in the directory, using the
     * specified snapshot ID.
     * 
     * @param blobName
     *            A <code>String</code> that represents the name of the blob.
     * @param snapshotID
     *            A <code>String</code> that represents the snapshot ID of the blob.
     * 
     * @return A {@link CloudPageBlob} object that represents a reference to the specified page blob.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     * @throws URISyntaxException
     *             If the resource URI is invalid.
     */
    public CloudPageBlob getPageBlobReference(final String blobName, final String snapshotID)
            throws URISyntaxException, StorageException {
        Utility.assertNotNullOrEmpty("blobName", blobName);

        final StorageUri address = PathUtility.appendPathToUri(this.storageUri, blobName);

        final CloudPageBlob retBlob = new CloudPageBlob(address, snapshotID, this.blobServiceClient);
        retBlob.setContainer(this);
        return retBlob;
    }

    /**
     * Returns the properties for the container.
     * 
     * @return A {@link BlobContainerProperties} object that represents the properties for the container.
     */
    public BlobContainerProperties getProperties() {
        return this.properties;
    }

    /**
     * Returns the Blob service client associated with this container.
     * 
     * @return A {@link CloudBlobClient} object that represents the service client associated with this container.
     */
    public CloudBlobClient getServiceClient() {
        return this.blobServiceClient;
    }

    /**
     * Returns the canonical name for shared access.
     * 
     * @return the canonical name for shared access.
     */
    private String getSharedAccessCanonicalName() {
        String accountName = this.getServiceClient().getCredentials().getAccountName();
        String containerName = this.getName();

        return String.format("/%s/%s", accountName, containerName);
    }

    /**
     * Returns the URI after applying the authentication transformation.
     * 
     * @return A <code>java.net.URI</code> object that represents the URI after applying the authentication
     *         transformation.
     * 
     * @throws IllegalArgumentException
     *             If an unexpected value is passed.
     * @throws StorageException
     *             If a storage service error occurred.
     * @throws URISyntaxException
     *             If the resource URI is invalid.
     */
    private StorageUri getTransformedAddress() throws URISyntaxException, StorageException {
        return this.blobServiceClient.getCredentials().transformUri(this.storageUri);
    }

    /**
     * Returns the URI for this container.
     * 
     * @return The absolute URI to the container.
     */
    public URI getUri() {
        return this.storageUri.getPrimaryUri();
    }

    /**
     * Returns an enumerable collection of blob items for the container.
     * 
     * @return An enumerable collection of {@link ListBlobItem} objects retrieved lazily that represents the items in
     *         this container.
     */
    @DoesServiceRequest
    public Iterable<ListBlobItem> listBlobs() {
        return this.listBlobs(null, false, EnumSet.noneOf(BlobListingDetails.class), null, null);
    }

    /**
     * Returns an enumerable collection of blob items for the container whose names begin with the specified prefix.
     * 
     * @param prefix
     *            A <code>String</code> that represents the blob name prefix. This value must be preceded either by the
     *            name of the container or by the absolute path to the container.
     * 
     * @return An enumerable collection of {@link ListBlobItem} objects retrieved lazily that represents the
     *         items whose names begin with the specified prefix in this container.
     */
    @DoesServiceRequest
    public Iterable<ListBlobItem> listBlobs(final String prefix) {
        return this.listBlobs(prefix, false, EnumSet.noneOf(BlobListingDetails.class), null, null);
    }

    /**
     * Returns an enumerable collection of blob items whose names begin with the specified prefix, using the specified
     * flat or hierarchical option, listing details options, request options, and operation context.
     * 
     * @param prefix
     *            A <code>String</code> that represents the blob name prefix. This value must be preceded either by the
     *            name of the container or by the absolute path to the container.
     * @param useFlatBlobListing
     *            <code>true</code> to indicate that the returned list will be flat; <code>false</code> to indicate that
     *            the returned list will be hierarchical.
     * @param listingDetails
     *            A <code>java.util.EnumSet</code> object that contains {@link BlobListingDetails} values that indicate
     *            whether snapshots, metadata, and/or uncommitted blocks are returned. Committed blocks are always
     *            returned.
     * @param options
     *            A {@link BlobRequestOptions} object that specifies any additional options for the request. Specifying
     *            <code>null</code> will use the default request options from the associated service client (
     *            {@link CloudBlobClient}).
     * @param opContext
     *            An {@link OperationContext} object that represents the context for the current operation. This object
     *            is used to track requests to the storage service, and to provide additional runtime information about
     *            the operation.
     * 
     * @return An enumerable collection of {@link ListBlobItem} objects retrieved lazily that represent the block
     *         items whose names begin with the specified prefix in this directory.
     */
    @DoesServiceRequest
    public Iterable<ListBlobItem> listBlobs(final String prefix, final boolean useFlatBlobListing,
            final EnumSet<BlobListingDetails> listingDetails, BlobRequestOptions options, OperationContext opContext) {
        if (opContext == null) {
            opContext = new OperationContext();
        }

        opContext.initialize();
        options = BlobRequestOptions.applyDefaults(options, BlobType.UNSPECIFIED, this.blobServiceClient);

        if (!useFlatBlobListing && listingDetails != null && listingDetails.contains(BlobListingDetails.SNAPSHOTS)) {
            throw new IllegalArgumentException(SR.SNAPSHOT_LISTING_ERROR);
        }

        SegmentedStorageRequest segmentedRequest = new SegmentedStorageRequest();

        return new LazySegmentedIterable<CloudBlobClient, CloudBlobContainer, ListBlobItem>(
                this.listBlobsSegmentedImpl(prefix, useFlatBlobListing, listingDetails, -1, options, segmentedRequest),
                this.blobServiceClient, this, options.getRetryPolicyFactory(), opContext);
    }

    /**
     * Returns a result segment of an enumerable collection of blob items in the container.
     * 
     * @return A {@link ResultSegment} object that contains a segment of the enumerable collection of
     *         {@link ListBlobItem} objects that represent the blob items in the container.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public ResultSegment<ListBlobItem> listBlobsSegmented() throws StorageException {
        return this.listBlobsSegmented(null, false, EnumSet.noneOf(BlobListingDetails.class), -1, null, null, null);
    }

    /**
     * Returns a result segment containing a collection of blob items whose names begin with the specified prefix.
     * 
     * @param prefix
     *            A <code>String</code> that represents the prefix of the blob name.
     * 
     * @return A {@link ResultSegment} object that contains a segment of the enumerable collection of
     *         {@link ListBlobItem} objects that represent the blob items whose names begin with the specified prefix in
     *         the container.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public ResultSegment<ListBlobItem> listBlobsSegmented(final String prefix) throws StorageException {
        return this.listBlobsSegmented(prefix, false, EnumSet.noneOf(BlobListingDetails.class), -1, null, null, null);
    }

    /**
     * Returns a result segment containing a collection of blob items whose names begin with the specified prefix, using
     * the specified flat or hierarchical option, listing details options, request options, and operation context.
     * 
     * @param prefix
     *            A <code>String</code> that represents the prefix of the blob name.
     * @param useFlatBlobListing
     *            <code>true</code> to indicate that the returned list will be flat; <code>false</code> to indicate that
     *            the returned list will be hierarchical.
     * @param listingDetails
     *            A <code>java.util.EnumSet</code> object that contains {@link BlobListingDetails} values that indicate
     *            whether snapshots, metadata, and/or uncommitted blocks are returned. Committed blocks are always
     *            returned.
     * @param maxResults
     *            The maximum number of results to retrieve.
     * @param continuationToken
     *            A {@link ResultContinuation} object that represents a continuation token returned by a previous
     *            listing operation.
     * @param options
     *            A {@link BlobRequestOptions} object that specifies any additional options for the request. Specifying
     *            <code>null</code> will use the default request options from the associated service client (
     *            {@link CloudBlobClient}).
     * @param opContext
     *            An {@link OperationContext} object that represents the context for the current operation. This object
     *            is used to track requests to the storage service, and to provide additional runtime information about
     *            the operation.
     * 
     * @return A {@link ResultSegment} object that contains a segment of the enumerable collection of
     *         {@link ListBlobItem} objects that represent the block items whose names begin with the specified prefix
     *         in the container.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public ResultSegment<ListBlobItem> listBlobsSegmented(final String prefix, final boolean useFlatBlobListing,
            final EnumSet<BlobListingDetails> listingDetails, final int maxResults,
            final ResultContinuation continuationToken, BlobRequestOptions options, OperationContext opContext)
            throws StorageException {
        if (opContext == null) {
            opContext = new OperationContext();
        }

        opContext.initialize();
        options = BlobRequestOptions.applyDefaults(options, BlobType.UNSPECIFIED, this.blobServiceClient);

        Utility.assertContinuationType(continuationToken, ResultContinuationType.BLOB);

        if (!useFlatBlobListing && listingDetails != null && listingDetails.contains(BlobListingDetails.SNAPSHOTS)) {
            throw new IllegalArgumentException(SR.SNAPSHOT_LISTING_ERROR);
        }

        SegmentedStorageRequest segmentedRequest = new SegmentedStorageRequest();
        segmentedRequest.setToken(continuationToken);

        return ExecutionEngine.executeWithRetry(this.blobServiceClient, this, this.listBlobsSegmentedImpl(prefix,
                useFlatBlobListing, listingDetails, maxResults, options, segmentedRequest), options
                .getRetryPolicyFactory(), opContext);
    }

    private StorageRequest<CloudBlobClient, CloudBlobContainer, ResultSegment<ListBlobItem>> listBlobsSegmentedImpl(
            final String prefix, final boolean useFlatBlobListing, final EnumSet<BlobListingDetails> listingDetails,
            final int maxResults, final BlobRequestOptions options, final SegmentedStorageRequest segmentedRequest) {

        Utility.assertContinuationType(segmentedRequest.getToken(), ResultContinuationType.BLOB);
        Utility.assertNotNull("options", options);

        final String delimiter = useFlatBlobListing ? null : this.blobServiceClient.getDirectoryDelimiter();

        final BlobListingContext listingContext = new BlobListingContext(prefix, maxResults, delimiter, listingDetails);

        final StorageRequest<CloudBlobClient, CloudBlobContainer, ResultSegment<ListBlobItem>> getRequest = new StorageRequest<CloudBlobClient, CloudBlobContainer, ResultSegment<ListBlobItem>>(
                options, this.getStorageUri()) {

            @Override
            public void setRequestLocationMode() {
                this.setRequestLocationMode(Utility.getListingLocationMode(segmentedRequest.getToken()));
            }

            @Override
            public HttpURLConnection buildRequest(CloudBlobClient client, CloudBlobContainer container,
                    OperationContext context) throws Exception {
                listingContext.setMarker(segmentedRequest.getToken() != null ? segmentedRequest.getToken()
                        .getNextMarker() : null);
                return BlobRequest.listBlobs(container.getTransformedAddress().getUri(this.getCurrentLocation()),
                        options, context, listingContext);
            }

            @Override
            public void signRequest(HttpURLConnection connection, CloudBlobClient client, OperationContext context)
                    throws Exception {
                StorageRequest.signBlobAndQueueRequest(connection, client, -1L, null);
            }

            @Override
            public ResultSegment<ListBlobItem> preProcessResponse(CloudBlobContainer container, CloudBlobClient client,
                    OperationContext context) throws Exception {
                if (this.getResult().getStatusCode() != HttpURLConnection.HTTP_OK) {
                    this.setNonExceptionedRetryableFailure(true);
                }

                return null;
            }

            @Override
            public ResultSegment<ListBlobItem> postProcessResponse(HttpURLConnection connection,
                    CloudBlobContainer container, CloudBlobClient client, OperationContext context,
                    ResultSegment<ListBlobItem> storageObject) throws Exception {
                final ListBlobsResponse response = BlobListHandler.getBlobList(connection.getInputStream(), container);

                ResultContinuation newToken = null;

                if (response.getNextMarker() != null) {
                    newToken = new ResultContinuation();
                    newToken.setNextMarker(response.getNextMarker());
                    newToken.setContinuationType(ResultContinuationType.BLOB);
                    newToken.setTargetLocation(this.getResult().getTargetLocation());
                }

                final ResultSegment<ListBlobItem> resSegment = new ResultSegment<ListBlobItem>(response.getResults(),
                        maxResults, newToken);

                // Important for listBlobs because this is required by the lazy iterator between executions.
                segmentedRequest.setToken(resSegment.getContinuationToken());

                return resSegment;
            }
        };

        return getRequest;
    }

    /**
     * Returns an enumerable collection of containers for the service client associated with this container.
     * 
     * @return An enumerable collection of {@link CloudBlobContainer} objects retrieved lazily that represent the
     *         containers for the service client associated with this container.
     */
    @DoesServiceRequest
    public Iterable<CloudBlobContainer> listContainers() {
        return this.blobServiceClient.listContainers();
    }

    /**
     * Returns an enumerable collection of containers whose names begin with the specified prefix for the service client
     * associated with this container.
     * 
     * @param prefix
     *            A <code>String</code> that represents the container name prefix.
     * 
     * @return An enumerable collection of {@link CloudBlobContainer} objects retrieved lazily that represent the
     *         containers whose names begin with the specified prefix for the service client associated with this
     *         container.
     */
    @DoesServiceRequest
    public Iterable<CloudBlobContainer> listContainers(final String prefix) {
        return this.blobServiceClient.listContainers(prefix);
    }

    /**
     * Returns an enumerable collection of containers whose names begin with the specified prefix for the service client
     * associated with this container, using the specified details setting, request options, and operation context.
     * 
     * @param prefix
     *            A <code>String</code> that represents the container name prefix.
     * @param detailsIncluded
     *            A {@link ContainerListingDetails} value that indicates whether container metadata will be returned.
     * @param options
     *            A {@link BlobRequestOptions} object that specifies any additional options for the request. Specifying
     *            <code>null</code> will use the default request options from the associated service client (
     *            {@link CloudBlobClient}).
     * @param opContext
     *            An {@link OperationContext} object that represents the context for the current operation. This object
     *            is used to track requests to the storage service, and to provide additional runtime information about
     *            the operation.
     * 
     * @return An enumerable collection of {@link CloudBlobContainer} objects retrieved lazily that represents the
     *         containers for the
     *         service client associated with this container.
     */
    @DoesServiceRequest
    public Iterable<CloudBlobContainer> listContainers(final String prefix,
            final ContainerListingDetails detailsIncluded, final BlobRequestOptions options,
            final OperationContext opContext) {
        return this.blobServiceClient.listContainers(prefix, detailsIncluded, options, opContext);
    }

    /**
     * Returns a result segment of an enumerable collection of containers for the service client associated with this
     * container.
     * 
     * @return A {@link ResultSegment} object that contains a segment of the enumerable collection of
     *         {@link CloudBlobContainer} objects that represent the containers for the service client associated with
     *         this container.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public ResultSegment<CloudBlobContainer> listContainersSegmented() throws StorageException {
        return this.blobServiceClient.listContainersSegmented();
    }

    /**
     * Returns a result segment of an enumerable collection of containers whose names begin with the specified prefix
     * for the service client associated with this container.
     * 
     * @param prefix
     *            A <code>String</code> that represents the blob name prefix.
     * 
     * @return A {@link ResultSegment} object that contains a segment of the enumerable collection of
     *         {@link CloudBlobContainer} objects that represent the containers whose names begin with the specified
     *         prefix for the service client associated with this container.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public ResultSegment<CloudBlobContainer> listContainersSegmented(final String prefix) throws StorageException {
        return this.blobServiceClient.listContainersSegmented(prefix);
    }

    /**
     * Returns a result segment containing a collection of containers whose names begin with the specified prefix for
     * the service client associated with this container, using the specified listing details options, request options,
     * and operation context.
     * 
     * @param prefix
     *            A <code>String</code> that represents the prefix of the container name.
     * @param detailsIncluded
     *            A {@link ContainerListingDetails} object that indicates whether metadata is included.
     * @param maxResults
     *            The maximum number of results to retrieve.
     * @param continuationToken
     *            A {@link ResultContinuation} object that represents a continuation token returned by a previous
     *            listing operation.
     * @param options
     *            A {@link BlobRequestOptions} object that specifies any additional options for the request. Specifying
     *            <code>null</code> will use the default request options from the associated service client (
     *            {@link CloudBlobClient}).
     * @param opContext
     *            An {@link OperationContext} object that represents the context for the current operation. This object
     *            is used to track requests to the storage service, and to provide additional runtime information about
     *            the operation.
     * 
     * @return A {@link ResultSegment} object that contains a segment of the enumerable collection of
     *         {@link CloudBlobContainer} objects that represent the containers whose names begin with the specified
     *         prefix for the service client associated with this container.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     * 
     */
    @DoesServiceRequest
    public ResultSegment<CloudBlobContainer> listContainersSegmented(final String prefix,
            final ContainerListingDetails detailsIncluded, final int maxResults,
            final ResultContinuation continuationToken, final BlobRequestOptions options,
            final OperationContext opContext) throws StorageException {
        return this.blobServiceClient.listContainersSegmented(prefix, detailsIncluded, maxResults, continuationToken,
                options, opContext);
    }

    /**
     * Parse URI for SAS (Shared access signature) information.
     * 
     * Validate that no other query parameters are passed in. Any SAS information will be recorded as corresponding
     * credentials instance. If existingClient is passed in, any SAS information found will not be supported. Otherwise
     * a new client is created based on SAS information or as anonymous credentials.
     * 
     * @param completeUri
     *            A {@link StorageUri} object which represents the complete URI.
     * @param existingClient
     *            A {@link CloudBlobClient} object which represents the client to use.
     * @param usePathStyleUris
     *            <code>true</code> if path-style URIs are used; otherwise, <code>false</code>.
     * @throws StorageException
     *             If a storage service error occurred.
     * @throws URISyntaxException
     *             If the resource URI is invalid.
     */
    private void parseQueryAndVerify(final StorageUri completeUri, final CloudBlobClient existingClient,
            final boolean usePathStyleUris) throws URISyntaxException, StorageException {
        Utility.assertNotNull("completeUri", completeUri);

        if (!completeUri.isAbsolute()) {
            final String errorMessage = String.format(SR.RELATIVE_ADDRESS_NOT_PERMITTED, completeUri.toString());
            throw new IllegalArgumentException(errorMessage);
        }

        this.storageUri = PathUtility.stripURIQueryAndFragment(completeUri);

        final HashMap<String, String[]> queryParameters = PathUtility.parseQueryString(completeUri.getQuery());
        final StorageCredentialsSharedAccessSignature sasCreds = SharedAccessSignatureHelper
                .parseQuery(queryParameters);

        if (sasCreds == null) {
            if (existingClient == null) {
                this.blobServiceClient = new CloudBlobClient(new URI(PathUtility.getServiceClientBaseAddress(
                        this.getUri(), usePathStyleUris)));
            }
            return;
        }

        final Boolean sameCredentials = existingClient == null ? false : Utility.areCredentialsEqual(sasCreds,
                existingClient.getCredentials());

        if (existingClient == null || !sameCredentials) {
            this.blobServiceClient = new CloudBlobClient(new URI(PathUtility.getServiceClientBaseAddress(this.getUri(),
                    usePathStyleUris)), sasCreds);
        }

        if (existingClient != null && !sameCredentials) {
            this.blobServiceClient.setDefaultRequestOptions(new BlobRequestOptions(existingClient
                    .getDefaultRequestOptions()));
            this.blobServiceClient.setDirectoryDelimiter(existingClient.getDirectoryDelimiter());
        }
    }

    void updatePropertiesFromResponse(HttpURLConnection request) {
        // ETag
        this.getProperties().setEtag(request.getHeaderField(Constants.HeaderConstants.ETAG));

        // Last Modified
        if (0 != request.getLastModified()) {
            final Calendar lastModifiedCalendar = Calendar.getInstance(Utility.LOCALE_US);
            lastModifiedCalendar.setTimeZone(Utility.UTC_ZONE);
            lastModifiedCalendar.setTime(new Date(request.getLastModified()));
            this.getProperties().setLastModified(lastModifiedCalendar.getTime());
        }
    }

    /**
     * Sets the metadata collection of name-value pairs to be set on the container with an {@link #uploadMetadata} call.
     * This collection will overwrite any existing container metadata. If this is set to an empty collection, the
     * container metadata will be cleared on an {@link #uploadMetadata} call.
     * 
     * @param metadata
     *            A <code>java.util.HashMap</code> object that represents the metadata being assigned to the container.
     */
    public void setMetadata(final HashMap<String, String> metadata) {
        this.metadata = metadata;
    }

    /**
     * Sets the name of the container.
     * 
     * @param name
     *            A <code>String</code> that represents the name being assigned to the container.
     */
    protected void setName(final String name) {
        this.name = name;
    }

    /**
     * Sets the list of URIs for all locations.
     * 
     * @param storageUri
     *            A {@link StorageUri} object which represents the list of URIs for all locations.
     */
    protected void setStorageUri(final StorageUri storageUri) {
        this.storageUri = storageUri;
    }

    /**
     * Sets the properties for the container.
     * 
     * @param properties
     *            A {@link BlobContainerProperties} object that represents the properties being assigned to the
     *            container.
     */
    protected void setProperties(final BlobContainerProperties properties) {
        this.properties = properties;
    }

    /**
     * Uploads the container's metadata.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public void uploadMetadata() throws StorageException {
        this.uploadMetadata(null /* accessCondition */, null /* options */, null /* opContext */);
    }

    /**
     * Uploads the container's metadata using the specified request options and operation context.
     * 
     * @param accessCondition
     *            An {@link AccessCondition} object that represents the access conditions for the container.
     * @param options
     *            A {@link BlobRequestOptions} object that specifies any additional options for the request. Specifying
     *            <code>null</code> will use the default request options from the associated service client (
     *            {@link CloudBlobClient}).
     * @param opContext
     *            An {@link OperationContext} object that represents the context for the current operation. This object
     *            is used to track requests to the storage service, and to provide additional runtime information about
     *            the operation.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public void uploadMetadata(AccessCondition accessCondition, BlobRequestOptions options, OperationContext opContext)
            throws StorageException {
        if (opContext == null) {
            opContext = new OperationContext();
        }

        opContext.initialize();
        options = BlobRequestOptions.applyDefaults(options, BlobType.UNSPECIFIED, this.blobServiceClient);

        ExecutionEngine.executeWithRetry(this.blobServiceClient, this,
                this.uploadMetadataImpl(accessCondition, options), options.getRetryPolicyFactory(), opContext);

    }

    @DoesServiceRequest
    private StorageRequest<CloudBlobClient, CloudBlobContainer, Void> uploadMetadataImpl(
            final AccessCondition accessCondition, final BlobRequestOptions options) {
        final StorageRequest<CloudBlobClient, CloudBlobContainer, Void> putRequest = new StorageRequest<CloudBlobClient, CloudBlobContainer, Void>(
                options, this.getStorageUri()) {

            @Override
            public HttpURLConnection buildRequest(CloudBlobClient client, CloudBlobContainer container,
                    OperationContext context) throws Exception {
                return BlobRequest.setContainerMetadata(container.getStorageUri().getUri(this.getCurrentLocation()),
                        options, context, accessCondition);
            }

            @Override
            public void setHeaders(HttpURLConnection connection, CloudBlobContainer container, OperationContext context) {
                BlobRequest.addMetadata(connection, container.metadata, context);
            }

            @Override
            public void signRequest(HttpURLConnection connection, CloudBlobClient client, OperationContext context)
                    throws Exception {
                StorageRequest.signBlobAndQueueRequest(connection, client, 0L, null);
            }

            @Override
            public Void preProcessResponse(CloudBlobContainer container, CloudBlobClient client,
                    OperationContext context) throws Exception {
                if (this.getResult().getStatusCode() != HttpURLConnection.HTTP_OK) {
                    this.setNonExceptionedRetryableFailure(true);
                }

                container.updatePropertiesFromResponse(this.getConnection());
                return null;
            }
        };

        return putRequest;
    }

    /**
     * Uploads the container's permissions.
     * 
     * @param permissions
     *            A {@link BlobContainerPermissions} object that represents the permissions to upload.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public void uploadPermissions(final BlobContainerPermissions permissions) throws StorageException {
        this.uploadPermissions(permissions, null /* accessCondition */, null /* options */, null /* opContext */);
    }

    /**
     * Uploads the container's permissions using the specified request options and operation context.
     * 
     * @param permissions
     *            A {@link BlobContainerPermissions} object that represents the permissions to upload.
     * @param accessCondition
     *            An {@link AccessCondition} object that represents the access conditions for the container.
     * @param options
     *            A {@link BlobRequestOptions} object that specifies any additional options for the request. Specifying
     *            <code>null</code> will use the default request options from the associated service client (
     *            {@link CloudBlobClient}).
     * @param opContext
     *            An {@link OperationContext} object that represents the context for the current operation. This object
     *            is used to track requests to the storage service, and to provide additional runtime information about
     *            the operation.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public void uploadPermissions(final BlobContainerPermissions permissions, final AccessCondition accessCondition,
            BlobRequestOptions options, OperationContext opContext) throws StorageException {
        if (opContext == null) {
            opContext = new OperationContext();
        }

        opContext.initialize();
        options = BlobRequestOptions.applyDefaults(options, BlobType.UNSPECIFIED, this.blobServiceClient);

        ExecutionEngine.executeWithRetry(this.blobServiceClient, this,
                uploadPermissionsImpl(permissions, accessCondition, options), options.getRetryPolicyFactory(),
                opContext);
    }

    private StorageRequest<CloudBlobClient, CloudBlobContainer, Void> uploadPermissionsImpl(
            final BlobContainerPermissions permissions, final AccessCondition accessCondition,
            final BlobRequestOptions options) throws StorageException {
        try {
            final StringWriter outBuffer = new StringWriter();
            SharedAccessPolicySerializer.writeSharedAccessIdentifiersToStream(permissions.getSharedAccessPolicies(),
                    outBuffer);
            final byte[] aclBytes = outBuffer.toString().getBytes(Constants.UTF8_CHARSET);
            final StorageRequest<CloudBlobClient, CloudBlobContainer, Void> putRequest = new StorageRequest<CloudBlobClient, CloudBlobContainer, Void>(
                    options, this.getStorageUri()) {
                @Override
                public HttpURLConnection buildRequest(CloudBlobClient client, CloudBlobContainer container,
                        OperationContext context) throws Exception {
                    this.setSendStream(new ByteArrayInputStream(aclBytes));
                    this.setLength((long) aclBytes.length);
                    return BlobRequest.setAcl(container.getStorageUri().getUri(this.getCurrentLocation()), options,
                            context, accessCondition, permissions.getPublicAccess());
                }

                @Override
                public void signRequest(HttpURLConnection connection, CloudBlobClient client, OperationContext context)
                        throws Exception {
                    StorageRequest.signBlobAndQueueRequest(connection, client, aclBytes.length, null);
                }

                @Override
                public Void preProcessResponse(CloudBlobContainer container, CloudBlobClient client,
                        OperationContext context) throws Exception {
                    if (this.getResult().getStatusCode() != HttpURLConnection.HTTP_OK) {
                        this.setNonExceptionedRetryableFailure(true);
                        return null;
                    }

                    container.updatePropertiesFromResponse(this.getConnection());
                    return null;
                }
            };

            return putRequest;
        }
        catch (final IllegalArgumentException e) {
            StorageException translatedException = StorageException.translateException(null, e, null);
            throw translatedException;
        }
        catch (final XMLStreamException e) {
            // The request was not even made. There was an error while trying to read the permissions. Just throw.
            StorageException translatedException = StorageException.translateException(null, e, null);
            throw translatedException;
        }
        catch (UnsupportedEncodingException e) {
            // The request was not even made. There was an error while trying to read the permissions. Just throw.
            StorageException translatedException = StorageException.translateException(null, e, null);
            throw translatedException;
        }
    }

    /**
     * Acquires a new lease on the container with the specified lease time and proposed lease ID.
     * 
     * @param leaseTimeInSeconds
     *            An <code>Integer</code> which specifies the span of time for which to acquire the lease, in seconds.
     *            If null, an infinite lease will be acquired. If not null, the value must be greater than
     *            zero.
     * 
     * @param proposedLeaseId
     *            A <code>String</code> that represents the proposed lease ID for the new lease,
     *            or null if no lease ID is proposed.
     * 
     * @return A <code>String</code> that represents the lease ID.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public final String acquireLease(final Integer leaseTimeInSeconds, final String proposedLeaseId)
            throws StorageException {
        return this.acquireLease(leaseTimeInSeconds, proposedLeaseId, null /* accessCondition */, null /* options */,
                null /* opContext */);
    }

    /**
     * Acquires a new lease on the container with the specified lease time, proposed lease ID, request
     * options, and operation context.
     * 
     * @param leaseTimeInSeconds
     *            An <code>Integer</code> which specifies the span of time for which to acquire the lease, in seconds.
     *            If null, an infinite lease will be acquired. If not null, the value must be greater than
     *            zero.
     * 
     * @param proposedLeaseId
     *            A <code>String</code> that represents the proposed lease ID for the new lease,
     *            or null if no lease ID is proposed.
     * 
     * @param accessCondition
     *            An {@link AccessCondition} object that represents the access conditions for the container.
     * 
     * @param options
     *            A {@link BlobRequestOptions} object that specifies any additional options for the request. Specifying
     *            <code>null</code> will use the default request options from the associated service client
     *            ({@link CloudBlobClient}).
     * 
     * @param opContext
     *            An {@link OperationContext} object that represents the context for the current operation. The context
     *            is used to track requests to the storage service, and to provide additional runtime information about
     *            the operation.
     * 
     * @return A <code>String</code> that represents the lease ID.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public final String acquireLease(final Integer leaseTimeInSeconds, final String proposedLeaseId,
            final AccessCondition accessCondition, BlobRequestOptions options, OperationContext opContext)
            throws StorageException {
        if (opContext == null) {
            opContext = new OperationContext();
        }

        opContext.initialize();
        options = BlobRequestOptions.applyDefaults(options, BlobType.UNSPECIFIED, this.blobServiceClient);

        return ExecutionEngine.executeWithRetry(this.blobServiceClient, this,
                this.acquireLeaseImpl(leaseTimeInSeconds, proposedLeaseId, accessCondition, options),
                options.getRetryPolicyFactory(), opContext);
    }

    private StorageRequest<CloudBlobClient, CloudBlobContainer, String> acquireLeaseImpl(
            final Integer leaseTimeInSeconds, final String proposedLeaseId, final AccessCondition accessCondition,
            final BlobRequestOptions options) {
        final StorageRequest<CloudBlobClient, CloudBlobContainer, String> putRequest = new StorageRequest<CloudBlobClient, CloudBlobContainer, String>(
                options, this.getStorageUri()) {

            @Override
            public HttpURLConnection buildRequest(CloudBlobClient client, CloudBlobContainer container,
                    OperationContext context) throws Exception {
                return BlobRequest
                        .leaseContainer(container.getStorageUri().getUri(this.getCurrentLocation()), options, context,
                                accessCondition, LeaseAction.ACQUIRE, leaseTimeInSeconds, proposedLeaseId, null /* breakPeriodInSeconds */);
            }

            @Override
            public void signRequest(HttpURLConnection connection, CloudBlobClient client, OperationContext context)
                    throws Exception {
                StorageRequest.signBlobAndQueueRequest(connection, client, 0L, null);
            }

            @Override
            public String preProcessResponse(CloudBlobContainer container, CloudBlobClient client,
                    OperationContext context) throws Exception {
                if (this.getResult().getStatusCode() != HttpURLConnection.HTTP_CREATED) {
                    this.setNonExceptionedRetryableFailure(true);
                    return null;
                }

                container.updatePropertiesFromResponse(this.getConnection());

                return BlobResponse.getLeaseID(this.getConnection());
            }

        };

        return putRequest;
    }

    /**
     * Renews an existing lease with the specified access conditions.
     * 
     * @param accessCondition
     *            An {@link AccessCondition} object that represents the access conditions for the container. The lease
     *            ID is required to be set with an access condition.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public final void renewLease(final AccessCondition accessCondition) throws StorageException {
        this.renewLease(accessCondition, null /* options */, null /* opContext */);
    }

    /**
     * Renews an existing lease with the specified access conditions, request options, and operation context.
     * 
     * @param accessCondition
     *            An {@link AccessCondition} object that represents the access conditions for the blob. The lease ID is
     *            required to be set with an access condition.
     * 
     * @param options
     *            A {@link BlobRequestOptions} object that specifies any additional options for the request. Specifying
     *            <code>null</code> will use the default request options from the associated service client
     *            ({@link CloudBlobClient}).
     * 
     * @param opContext
     *            An {@link OperationContext} object that represents the context for the current operation. The context
     *            is used to track requests to the storage service, and to provide additional runtime information about
     *            the operation.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public final void renewLease(final AccessCondition accessCondition, BlobRequestOptions options,
            OperationContext opContext) throws StorageException {
        Utility.assertNotNull("accessCondition", accessCondition);
        Utility.assertNotNullOrEmpty("leaseID", accessCondition.getLeaseID());

        if (opContext == null) {
            opContext = new OperationContext();
        }

        opContext.initialize();
        options = BlobRequestOptions.applyDefaults(options, BlobType.UNSPECIFIED, this.blobServiceClient);

        ExecutionEngine.executeWithRetry(this.blobServiceClient, this, this.renewLeaseImpl(accessCondition, options),
                options.getRetryPolicyFactory(), opContext);
    }

    private StorageRequest<CloudBlobClient, CloudBlobContainer, Void> renewLeaseImpl(
            final AccessCondition accessCondition, final BlobRequestOptions options) {
        final StorageRequest<CloudBlobClient, CloudBlobContainer, Void> putRequest = new StorageRequest<CloudBlobClient, CloudBlobContainer, Void>(
                options, this.getStorageUri()) {

            @Override
            public HttpURLConnection buildRequest(CloudBlobClient client, CloudBlobContainer container,
                    OperationContext context) throws Exception {
                return BlobRequest.leaseContainer(container.getStorageUri().getUri(this.getCurrentLocation()), options,
                        context, accessCondition, LeaseAction.RENEW, null /* leaseTimeInSeconds */,
                        null /* proposedLeaseId */, null /* breakPeriodInseconds */);
            }

            @Override
            public void signRequest(HttpURLConnection connection, CloudBlobClient client, OperationContext context)
                    throws Exception {
                StorageRequest.signBlobAndQueueRequest(connection, client, 0L, null);
            }

            @Override
            public Void preProcessResponse(CloudBlobContainer container, CloudBlobClient client,
                    OperationContext context) throws Exception {
                if (this.getResult().getStatusCode() != HttpURLConnection.HTTP_OK) {
                    this.setNonExceptionedRetryableFailure(true);
                    return null;
                }

                container.updatePropertiesFromResponse(this.getConnection());
                return null;
            }
        };

        return putRequest;
    }

    /**
     * Releases the lease on the container.
     * 
     * @param accessCondition
     *            An {@link AccessCondition} object that represents the access conditions for the blob. The lease ID is
     *            required to be set with an access condition.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public final void releaseLease(final AccessCondition accessCondition) throws StorageException {
        this.releaseLease(accessCondition, null /* options */, null /* opContext */);
    }

    /**
     * Releases the lease on the container using the specified access conditions, request options, and operation
     * context.
     * 
     * @param accessCondition
     *            An {@link AccessCondition} object that represents the access conditions for the blob. The lease ID is
     *            required to be set with an access condition.
     * 
     * @param options
     *            A {@link BlobRequestOptions} object that specifies any additional options for the request. Specifying
     *            <code>null</code> will use the default request options from the associated service client
     *            ({@link CloudBlobClient}).
     * 
     * @param opContext
     *            An {@link OperationContext} object that represents the context for the current operation. The context
     *            is used to track requests to the storage service, and to provide additional runtime information about
     *            the operation.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public final void releaseLease(final AccessCondition accessCondition, BlobRequestOptions options,
            OperationContext opContext) throws StorageException {
        Utility.assertNotNull("accessCondition", accessCondition);
        Utility.assertNotNullOrEmpty("leaseID", accessCondition.getLeaseID());

        if (opContext == null) {
            opContext = new OperationContext();
        }

        opContext.initialize();
        options = BlobRequestOptions.applyDefaults(options, BlobType.UNSPECIFIED, this.blobServiceClient);

        ExecutionEngine.executeWithRetry(this.blobServiceClient, this, this.releaseLeaseImpl(accessCondition, options),
                options.getRetryPolicyFactory(), opContext);
    }

    private StorageRequest<CloudBlobClient, CloudBlobContainer, Void> releaseLeaseImpl(
            final AccessCondition accessCondition, final BlobRequestOptions options) {
        final StorageRequest<CloudBlobClient, CloudBlobContainer, Void> putRequest = new StorageRequest<CloudBlobClient, CloudBlobContainer, Void>(
                options, this.getStorageUri()) {

            @Override
            public HttpURLConnection buildRequest(CloudBlobClient client, CloudBlobContainer container,
                    OperationContext context) throws Exception {
                return BlobRequest.leaseContainer(container.getStorageUri().getUri(this.getCurrentLocation()), options,
                        context, accessCondition, LeaseAction.RELEASE, null, null, null);
            }

            @Override
            public void signRequest(HttpURLConnection connection, CloudBlobClient client, OperationContext context)
                    throws Exception {
                StorageRequest.signBlobAndQueueRequest(connection, client, 0L, null);
            }

            @Override
            public Void preProcessResponse(CloudBlobContainer container, CloudBlobClient client,
                    OperationContext context) throws Exception {
                if (this.getResult().getStatusCode() != HttpURLConnection.HTTP_OK) {
                    this.setNonExceptionedRetryableFailure(true);
                    return null;
                }

                container.updatePropertiesFromResponse(this.getConnection());
                return null;
            }
        };

        return putRequest;
    }

    /**
     * Breaks the lease and ensures that another client cannot acquire a new lease until the current lease
     * period has expired.
     * 
     * @param breakPeriodInSeconds
     *            An <code>Integer</code> which specifies the time to wait, in seconds, until the current lease is
     *            broken.
     *            If null, the break period is the remainder of the current lease, or zero for infinite leases.
     * 
     * @return The time, in seconds, remaining in the lease period.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public final long breakLease(final Integer breakPeriodInSeconds) throws StorageException {
        return this.breakLease(breakPeriodInSeconds, null /* accessCondition */, null, null);
    }

    /**
     * Breaks the existing lease, using the specified request options and operation context, and ensures that
     * another client cannot acquire a new lease until the current lease period has expired.
     * 
     * @param breakPeriodInSeconds
     *            An <code>Integer</code> which specifies the time to wait, in seconds, until the current lease is
     *            broken.
     *            If null, the break period is the remainder of the current lease, or zero for infinite leases.
     * 
     * @param accessCondition
     *            An {@link AccessCondition} object that represents the access conditions for the blob.
     * @param options
     *            A {@link BlobRequestOptions} object that specifies any additional options for the request. Specifying
     *            <code>null</code> will use the default request options from the associated service client
     *            ({@link CloudBlobClient}).
     * @param opContext
     *            An {@link OperationContext} object that represents the context for the current operation. The context
     *            is used to track requests to the storage service, and to provide additional runtime information about
     *            the operation.
     * 
     * @return The time, in seconds, remaining in the lease period.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public final long breakLease(final Integer breakPeriodInSeconds, final AccessCondition accessCondition,
            BlobRequestOptions options, OperationContext opContext) throws StorageException {
        if (opContext == null) {
            opContext = new OperationContext();
        }

        if (breakPeriodInSeconds != null) {
            Utility.assertGreaterThanOrEqual("breakPeriodInSeconds", breakPeriodInSeconds, 0);
        }

        opContext.initialize();
        options = BlobRequestOptions.applyDefaults(options, BlobType.UNSPECIFIED, this.blobServiceClient);

        return ExecutionEngine.executeWithRetry(this.blobServiceClient, this,
                this.breakLeaseImpl(breakPeriodInSeconds, accessCondition, options), options.getRetryPolicyFactory(),
                opContext);
    }

    private final StorageRequest<CloudBlobClient, CloudBlobContainer, Long> breakLeaseImpl(
            final Integer breakPeriodInSeconds, final AccessCondition accessCondition, final BlobRequestOptions options) {
        final StorageRequest<CloudBlobClient, CloudBlobContainer, Long> putCmd = new StorageRequest<CloudBlobClient, CloudBlobContainer, Long>(
                options, this.getStorageUri()) {

            @Override
            public HttpURLConnection buildRequest(CloudBlobClient client, CloudBlobContainer container,
                    OperationContext context) throws Exception {
                return BlobRequest.leaseContainer(container.getStorageUri().getUri(this.getCurrentLocation()), options,
                        context, accessCondition, LeaseAction.BREAK, null, null, breakPeriodInSeconds);
            }

            @Override
            public void signRequest(HttpURLConnection connection, CloudBlobClient client, OperationContext context)
                    throws Exception {
                StorageRequest.signBlobAndQueueRequest(connection, client, 0L, null);
            }

            @Override
            public Long preProcessResponse(CloudBlobContainer container, CloudBlobClient client,
                    OperationContext context) throws Exception {
                if (this.getResult().getStatusCode() != HttpURLConnection.HTTP_ACCEPTED) {
                    this.setNonExceptionedRetryableFailure(true);
                    return -1L;
                }

                container.updatePropertiesFromResponse(this.getConnection());
                final String leaseTime = BlobResponse.getLeaseTime(this.getConnection());
                return Utility.isNullOrEmpty(leaseTime) ? -1L : Long.parseLong(leaseTime);
            }
        };

        return putCmd;
    }

    /**
     * Changes the existing lease ID to the proposed lease ID.
     * 
     * @param proposedLeaseId
     *            A <code>String</code> that represents the proposed lease ID for the new lease,
     *            or null if no lease ID is proposed.
     * 
     * @param accessCondition
     *            An {@link AccessCondition} object that represents the access conditions for the blob. The lease ID is
     *            required to be set with an access condition.
     * @return A <code>String</code> that represents the new lease ID.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public final String changeLease(final String proposedLeaseId, final AccessCondition accessCondition)
            throws StorageException {
        return this.changeLease(proposedLeaseId, accessCondition, null, null);
    }

    /**
     * Changes the existing lease ID to the proposed lease Id with the specified access conditions, request options,
     * and operation context.
     * 
     * @param proposedLeaseId
     *            A <code>String</code> that represents the proposed lease ID for the new lease. This cannot be null.
     * 
     * @param accessCondition
     *            An {@link AccessCondition} object that represents the access conditions for the blob. The lease ID is
     *            required to be set with an access condition.
     * 
     * @param options
     *            A {@link BlobRequestOptions} object that specifies any additional options for the request. Specifying
     *            <code>null</code> will use the default request options from the associated service client
     *            ({@link CloudBlobClient}).
     * 
     * @param opContext
     *            An {@link OperationContext} object that represents the context for the current operation. The context
     *            is used to track requests to the storage service, and to provide additional runtime information about
     *            the operation.
     * @return A <code>String</code> that represents the new lease ID.
     * 
     * @throws StorageException
     *             If a storage service error occurred.
     */
    @DoesServiceRequest
    public final String changeLease(final String proposedLeaseId, final AccessCondition accessCondition,
            BlobRequestOptions options, OperationContext opContext) throws StorageException {
        Utility.assertNotNull("proposedLeaseId", proposedLeaseId);
        Utility.assertNotNull("accessCondition", accessCondition);
        Utility.assertNotNullOrEmpty("leaseID", accessCondition.getLeaseID());

        if (opContext == null) {
            opContext = new OperationContext();
        }

        opContext.initialize();
        options = BlobRequestOptions.applyDefaults(options, BlobType.UNSPECIFIED, this.blobServiceClient);

        return ExecutionEngine.executeWithRetry(this.blobServiceClient, this,
                this.changeLeaseImpl(proposedLeaseId, accessCondition, options), options.getRetryPolicyFactory(),
                opContext);
    }

    private final StorageRequest<CloudBlobClient, CloudBlobContainer, String> changeLeaseImpl(
            final String proposedLeaseId, final AccessCondition accessCondition, final BlobRequestOptions options) {
        final StorageRequest<CloudBlobClient, CloudBlobContainer, String> putRequest = new StorageRequest<CloudBlobClient, CloudBlobContainer, String>(
                options, this.getStorageUri()) {

            @Override
            public HttpURLConnection buildRequest(CloudBlobClient client, CloudBlobContainer container,
                    OperationContext context) throws Exception {
                return BlobRequest.leaseContainer(container.getStorageUri().getUri(this.getCurrentLocation()), options,
                        context, accessCondition, LeaseAction.CHANGE, null, proposedLeaseId, null);
            }

            @Override
            public void signRequest(HttpURLConnection connection, CloudBlobClient client, OperationContext context)
                    throws Exception {
                StorageRequest.signBlobAndQueueRequest(connection, client, 0L, null);
            }

            @Override
            public String preProcessResponse(CloudBlobContainer container, CloudBlobClient client,
                    OperationContext context) throws Exception {
                if (this.getResult().getStatusCode() != HttpURLConnection.HTTP_OK) {
                    this.setNonExceptionedRetryableFailure(true);
                    return null;
                }

                container.updatePropertiesFromResponse(this.getConnection());
                return BlobResponse.getLeaseID(this.getConnection());
            }
        };

        return putRequest;
    }
}