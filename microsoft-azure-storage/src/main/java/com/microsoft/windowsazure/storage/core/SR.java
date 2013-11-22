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

package com.microsoft.windowsazure.storage.core;

/**
 * RESERVED FOR INTERNAL USE. Provides a standard set of errors that could be thrown from the client library.
 */
public class SR {
    public static final String ACCOUNT_NAME_NULL_OR_EMPTY = "The account name is null or empty.";
    public static final String ARGUMENT_NULL_OR_EMPTY = "The argument must not be null or an empty string. Argument name: %s.";
    public static final String ARGUMENT_OUT_OF_RANGE_ERROR = "The argument is out of range. Argument name: %s, Value passed: %s.";
    public static final String ATTEMPTED_TO_SERIALIZE_INACCESSIBLE_PROPERTY = "An attempt was made to access an inaccessible member of the entity during serialization.";
    public static final String BLOB_DATA_CORRUPTED = "Blob data corrupted (integrity check failed), Expected value is %s, retrieved %s";
    public static final String BLOB_ENDPOINT_NOT_CONFIGURED = "No blob endpoint configured.";
    public static final String BLOB_HASH_MISMATCH = "Blob hash mismatch (integrity check failed), Expected value is %s, retrieved %s.";
    public static final String BLOB_MD5_NOT_SUPPORTED_FOR_PAGE_BLOBS = "Blob level MD5 is not supported for page blobs.";
    public static final String BLOB_TYPE_NOT_DEFINED = "The blob type is not defined.  Allowed types are BlobType.BLOCK_BLOB and BlobType.Page_BLOB.";
    public static final String CANNOT_CREATE_SAS_FOR_GIVEN_CREDENTIALS = "Cannot create Shared Access Signature as the credentials does not have account name information. Please check that the credentials provided support creating Shared Access Signature.";
    public static final String CANNOT_CREATE_SAS_FOR_SNAPSHOTS = "Cannot create Shared Access Signature via references to blob snapshots. Please perform the given operation on the root blob instead.";
    public static final String CANNOT_CREATE_SAS_WITHOUT_ACCOUNT_KEY = "Cannot create Shared Access Signature unless the Account Key credentials are used by the ServiceClient.";
    public static final String CONTENT_LENGTH_MISMATCH = "An incorrect number of bytes was read from the connection. The connection may have been closed.";
    public static final String CREDENTIALS_CANNOT_SIGN_REQUEST = "CloudBlobClient, CloudQueueClient and CloudTableClient require credentials that can sign a request.";
    public static final String CUSTOM_RESOLVER_THREW = "The custom property resolver delegate threw an exception. Check the inner exception for more details.";
    public static final String DEFAULT_SERVICE_VERSION_ONLY_SET_FOR_BLOB_SERVICE = "DefaultServiceVersion can only be set for the Blob service.";
    public static final String DELETE_SNAPSHOT_NOT_VALID_ERROR = "The option '%s' must be 'None' to delete a specific snapshot specified by '%s'.";
    public static final String EDMTYPE_WAS_NULL = "EdmType cannot be null.";
    public static final String EMPTY_BATCH_NOT_ALLOWED = "Cannot execute an empty batch operation.";
    public static final String ENDPOINT_INFORMATION_UNAVAILABLE = "Endpoint information not available for Account using Shared Access Credentials.";
    public static final String ENTITY_PROPERTY_CANNOT_BE_NULL_FOR_PRIMITIVES = "EntityProperty cannot be set to null for primitive value types.";
    public static final String ETAG_INVALID_FOR_DELETE = "Delete requires a valid ETag (which may be the '*' wildcard).";
    public static final String ETAG_INVALID_FOR_MERGE = "Merge requires a valid ETag (which may be the '*' wildcard).";
    public static final String ETAG_INVALID_FOR_UPDATE = "Replace requires a valid ETag (which may be the '*' wildcard).";
    public static final String EXCEPTION_THROWN_DURING_DESERIALIZATION = "The entity threw an exception during deserialization.";
    public static final String EXCEPTION_THROWN_DURING_SERIALIZATION = "The entity threw an exception during serialization.";
    public static final String EXPECTED_A_FIELD_NAME = "Expected a field name.";
    public static final String EXPECTED_END_ARRAY = "Expected the end of a JSON Array.";
    public static final String EXPECTED_END_OBJECT = "Expected the end of a JSON Object.";
    public static final String EXPECTED_START_ARRAY = "Expected the start of a JSON Array.";
    public static final String EXPECTED_START_ELEMENT_TO_EQUAL_ERROR = "Expected START_ELEMENT to equal error.";
    public static final String EXPECTED_START_OBJECT = "Expected the start of a JSON Object.";
    public static final String FAILED_TO_PARSE_PROPERTY = "Failed to parse property '%s' with value '%s' as type '%s'";
    public static final String INCORRECT_STREAM_LENGTH = "An incorrect stream length was specified, resulting in an authentication failure. Please specify correct length, or -1.";
    public static final String INPUT_STREAM_SHOULD_BE_MARKABLE = "Input stream must be markable.";
    public static final String INVALID_ACCOUNT_NAME = "Invalid account name.";
    public static final String INVALID_ACL_ACCESS_TYPE = "Invalid acl public access type returned '%s'. Expected blob or container.";
    public static final String INVALID_BLOB_TYPE = "Incorrect Blob type, please use the correct Blob type to access a blob on the server. Expected %s, actual %s.";
    public static final String INVALID_BLOCK_ID = "Invalid blockID, blockID must be a valid Base64 String.";
    public static final String INVALID_CONDITIONAL_HEADERS = "The conditionals specified for this operation did not match server.";
    public static final String INVALID_CONNECTION_STRING = "Invalid connection string.";
    public static final String INVALID_CONNECTION_STRING_DEV_STORE_NOT_TRUE = "Invalid connection string, the UseDevelopmentStorage key must always be paired with 'true'.  Remove the flag entirely otherwise.";
    public static final String INVALID_CONTENT_LENGTH = "ContentLength must be set to -1 or positive Long value.";
    public static final String INVALID_CONTENT_TYPE = "An incorrect Content-Type was returned from the server.";
    public static final String INVALID_CORS_RULE = "A CORS rule must contain at least one allowed origin and allowed method, and MaxAgeInSeconds cannot have a value less than zero.";
    public static final String INVALID_DATE_STRING = "Invalid Date String: %s.";
    public static final String INVALID_EDMTYPE_VALUE = "Invalid value '%s' for EdmType.";
    public static final String INVALID_GEO_REPLICATION_STATUS = "Null or Invalid geo-replication status in response: %s.";
    public static final String INVALID_KEY = "Storage Key is not a valid base64 encoded string.";
    public static final String INVALID_MAX_WRITE_SIZE = "Max write size is 4MB. Please specify a smaller range.";
    public static final String INVALID_MESSAGE_LENGTH = "The message size cannot be larger than %s bytes.";
    public static final String INVALID_MIME_RESPONSE = "Invalid MIME response received.";
    public static final String INVALID_NUMBER_OF_BYTES_IN_THE_BUFFER = "Page data must be a multiple of 512 bytes. Buffer currently contains %d bytes.";
    public static final String INVALID_OPERATION_FOR_A_SNAPSHOT = "Cannot perform this operation on a blob representing a snapshot.";
    public static final String INVALID_PAGE_BLOB_LENGTH = "Page blob length must be multiple of 512.";
    public static final String INVALID_PAGE_START_OFFSET = "Page start offset must be multiple of 512.";
    public static final String INVALID_RANGE_CONTENT_MD5_HEADER = "Cannot specify x-ms-range-get-content-md5 header on ranges larger than 4 MB. Either use a BlobReadStream via openRead, or disable TransactionalMD5 via the BlobRequestOptions.";
    public static final String INVALID_RESPONSE_RECEIVED = "The response received is invalid or improperly formatted.";
    public static final String INVALID_STORAGE_PROTOCOL_VERSION = "Storage protocol version prior to 2009-09-19 do not support shared key authentication.";
    public static final String INVALID_STREAM_LENGTH = "Invalid stream length; stream must be between 0 and %s MB in length.";
    public static final String KEY_NULL = "Key invalid. Cannot be null.";
    public static final String LEASE_CONDITION_ON_SOURCE = "A lease condition cannot be specified on the source of a copy.";
    public static final String MARK_EXPIRED = "Stream mark expired.";
    public static final String MISSING_CREDENTIALS = "No credentials provided.";
    public static final String MISSING_MANDATORY_DATE_HEADER = "Canonicalization did not find a non-empty x-ms-date header in the request. Please use a request with a valid x-ms-date header in RFC 123 format.";
    public static final String MISSING_MANDATORY_PARAMETER_FOR_SAS = "Missing mandatory parameters for valid Shared Access Signature.";
    public static final String MISSING_MD5 = "ContentMD5 header is missing in the response.";
    public static final String MISSING_NULLARY_CONSTRUCTOR = "Class type must contain contain a nullary constructor.";
    public static final String OPS_IN_BATCH_MUST_HAVE_SAME_PARTITION_KEY = "All entities in a given batch must have the same partition key.";
    public static final String PARAMETER_NOT_IN_RANGE = "The value of the parameter '%s' should be between %s and %s.";
    public static final String PARAMETER_SHOULD_BE_GREATER = "The value of the parameter '%s' should be greater than %s.";
    public static final String PARTITIONKEY_MISSING_FOR_DELETE = "Delete requires a partition key.";
    public static final String PARTITIONKEY_MISSING_FOR_MERGE = "Merge requires a partition key.";
    public static final String PARTITIONKEY_MISSING_FOR_UPDATE = "Replace requires a partition key.";
    public static final String PARTITIONKEY_MISSING_FOR_UPSERT = "Upsert requires a partition key.";
    public static final String PATH_STYLE_URI_MISSING_ACCOUNT_INFORMATION = "Missing account name information inside path style URI. Path style URIs should be of the form http://<IPAddress:Port>/<accountName>";
    public static final String PERMISSIONS_COULD_NOT_BE_PARSED = "Permissions could not be parsed from '%s'.";
    public static final String PRIMARY_ONLY_COMMAND = "This operation can only be executed against the primary storage location.";
    public static final String PROPERTY_CANNOT_BE_SERIALIZED_AS_GIVEN_EDMTYPE = "Property %s with Edm Type %s cannot be de-serialized.";
    public static final String QUERY_PARAMETER_NULL_OR_EMPTY = "Cannot encode a query parameter with a null or empty key.";
    public static final String QUERY_REQUIRES_VALID_CLASSTYPE_OR_RESOLVER = "Query requires a valid class type or resolver.";
    public static final String QUEUE_ENDPOINT_NOT_CONFIGURED = "No queue endpoint configured.";
    public static final String RELATIVE_ADDRESS_NOT_PERMITTED = "Address %s is a relative address. Only absolute addresses are permitted.";
    public static final String RESPONSE_RECEIVED_IS_INVALID = "The response received is invalid or improperly formatted.";
    public static final String RETRIEVE_MUST_BE_ONLY_OPERATION_IN_BATCH = "A batch transaction with a retrieve operation cannot contain any other operations.";
    public static final String ROWKEY_MISSING_FOR_DELETE = "Delete requires a row key.";
    public static final String ROWKEY_MISSING_FOR_MERGE = "Merge requires a row key.";
    public static final String ROWKEY_MISSING_FOR_UPDATE = "Replace requires a row key.";
    public static final String ROWKEY_MISSING_FOR_UPSERT = "Upsert requires a row key.";
    public static final String SCHEME_NULL_OR_EMPTY = "The protocol to use is null. Please specify whether to use http or https.";
    public static final String SECONDARY_ONLY_COMMAND = "This operation can only be executed against the secondary storage location.";
    public static final String SNAPSHOT_LISTING_ERROR = "Listing snapshots is only supported in flat mode (no delimiter). Consider setting useFlatBlobListing to true.";
    public static final String SNAPSHOT_QUERY_OPTION_ALREADY_DEFINED = "Snapshot query parameter is already defined in the blob URI. Either pass in a snapshotTime parameter or use a full URL with a snapshot query parameter.";
    public static final String STORAGE_CREDENTIALS_NULL = "StorageCredentials cannot be null for the queue service.";
    public static final String STORAGE_URI_MISSING_LOCATION = "The URI for the target storage location is not specified. Please consider changing the request's location mode.";
    public static final String STORAGE_URI_MUST_MATCH = "Primary and secondary location URIs in a StorageUri must point to the same resource.";
    public static final String STOREAS_DIFFERENT_FOR_GETTER_AND_SETTER = "StoreAs Annotation found for both getter and setter for property %s with unequal values.";
    public static final String STOREAS_USED_ON_EMPTY_PROPERTY = "StoreAs Annotation found for property %s with empty value.";
    public static final String STREAM_CLOSED = "Stream is already closed.";
    public static final String STREAM_LENGTH_GREATER_THAN_4MB = "Invalid stream length, length must be less than or equal to 4 MB in size.";
    public static final String STREAM_LENGTH_NEGATIVE = "Invalid stream length, specify -1 for unknown length stream, or a positive number of bytes.";
    public static final String STRING_NOT_VALID = "The String is not a valid Base64-encoded string.";
    public static final String TABLE_ENDPOINT_NOT_CONFIGURED = "No table endpoint configured.";
    public static final String TABLE_OBJECT_RELATIVE_URIS_NOT_SUPPORTED = "Table Object relative URIs not supported.";
    public static final String TAKE_COUNT_ZERO_OR_NEGATIVE = "Take count must be positive and greater than 0.";
    public static final String TOO_MANY_SHARED_ACCESS_POLICY_IDENTIFIERS = "Too many %d shared access policy identifiers provided. Server does not support setting more than %d on a single container, queue, or table.";
    public static final String TOO_MANY_SHARED_ACCESS_POLICY_IDS = "Too many %d shared access policy identifiers provided. Server does not support setting more than %d on a single container.";
    public static final String TYPE_NOT_SUPPORTED = "Type %s is not supported.";
    public static final String UNEXPECTED_CONTINUATION_TYPE = "The continuation type passed in is unexpected. Please verify that the correct continuation type is passed in. Expected {%s}, found {%s}.";
    public static final String UNEXPECTED_FIELD_NAME = "Unexpected field name. Expected: '%s'. Actual: '%s'.";
    public static final String UNEXPECTED_STATUS_CODE_RECEIVED = "Unexpected http status code received.";
    public static final String UNEXPECTED_STREAM_READ_ERROR = "Unexpected error. Stream returned unexpected number of bytes.";
    public static final String UNKNOWN_TABLE_OPERATION = "Unknown table operation.";
}