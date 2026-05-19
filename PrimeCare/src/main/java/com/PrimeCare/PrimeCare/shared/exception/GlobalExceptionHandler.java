package com.PrimeCare.PrimeCare.shared.exception;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.PrimeCare.PrimeCare.shared.common.ApiError;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest req) {
        ErrorCode code = ex.getErrorCode();

        if (code.getStatus().is5xxServerError()) {
            log.error("ApiException at path={} message={}", req.getRequestURI(), ex.getMessage(), ex);
        } else {
            log.warn("ApiException at path={} code={} message={}", req.getRequestURI(), code.name(), ex.getMessage());
        }

        ApiError err = ApiError.builder()
                               .success(false)
                               .code(code.name())
                               .message(ex.getMessage())
                               .timestamp(Instant.now())
                               .status(code.getStatus().value())
                               .path(req.getRequestURI())
                               .details(ex.getDetails())
                               .build();

        return ResponseEntity.status(code.getStatus()).body(err);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage());
        }

        log.warn("Validation failed at path={} details={}", req.getRequestURI(), fields);

        ApiError err = ApiError.builder()
                               .success(false)
                               .code(ErrorCode.VALIDATION_ERROR.name())
                               .message(ErrorCode.VALIDATION_ERROR.getDefaultMessage())
                               .timestamp(Instant.now())
                               .status(ErrorCode.VALIDATION_ERROR.getStatus().value())
                               .path(req.getRequestURI())
                               .details(Map.of("fields", fields))
                               .build();

        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus()).body(err);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, String> fields = new HashMap<>();
        ex.getConstraintViolations().forEach(v ->
                fields.put(v.getPropertyPath().toString(), v.getMessage())
        );

        log.warn("Constraint violation at path={} details={}", req.getRequestURI(), fields);

        ApiError err = ApiError.builder()
                               .success(false)
                               .code(ErrorCode.VALIDATION_ERROR.name())
                               .message(ErrorCode.VALIDATION_ERROR.getDefaultMessage())
                               .timestamp(Instant.now())
                               .status(ErrorCode.VALIDATION_ERROR.getStatus().value())
                               .path(req.getRequestURI())
                               .details(Map.of("fields", fields))
                               .build();

        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus()).body(err);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest req) {
        log.warn("Optimistic lock conflict at path={} entity={}", req.getRequestURI(), ex.getPersistentClassName());

        ApiError err = ApiError.builder()
                               .success(false)
                               .code("CONCURRENT_MODIFICATION")
                               .message("Dữ liệu đã được cập nhật bởi người khác, vui lòng tải lại và thử lại")
                               .timestamp(Instant.now())
                               .status(HttpStatus.CONFLICT.value())
                               .path(req.getRequestURI())
                               .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(err);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.error("DataIntegrityViolation at path={}", req.getRequestURI(), ex);

        ApiError err = ApiError.builder()
                               .success(false)
                               .code(ErrorCode.INVALID_REQUEST.name())
                               .message("Dữ liệu vi phạm ràng buộc hệ thống")
                               .timestamp(Instant.now())
                               .status(ErrorCode.INVALID_REQUEST.getStatus().value())
                               .path(req.getRequestURI())
                               .build();

        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus()).body(err);
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ApiError> handleExpired(ExpiredJwtException ex, HttpServletRequest req) {
        log.warn("Expired JWT at path={}", req.getRequestURI());

        ApiError err = ApiError.builder()
                               .success(false)
                               .code(ErrorCode.AUTH_TOKEN_EXPIRED.name())
                               .message(ErrorCode.AUTH_TOKEN_EXPIRED.getDefaultMessage())
                               .timestamp(Instant.now())
                               .status(ErrorCode.AUTH_TOKEN_EXPIRED.getStatus().value())
                               .path(req.getRequestURI())
                               .build();

        return ResponseEntity.status(ErrorCode.AUTH_TOKEN_EXPIRED.getStatus()).body(err);
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiError> handleJwt(JwtException ex, HttpServletRequest req) {
        log.warn("Invalid JWT at path={}", req.getRequestURI());

        ApiError err = ApiError.builder()
                               .success(false)
                               .code(ErrorCode.AUTH_TOKEN_INVALID.name())
                               .message(ErrorCode.AUTH_TOKEN_INVALID.getDefaultMessage())
                               .timestamp(Instant.now())
                               .status(ErrorCode.AUTH_TOKEN_INVALID.getStatus().value())
                               .path(req.getRequestURI())
                               .build();

        return ResponseEntity.status(ErrorCode.AUTH_TOKEN_INVALID.getStatus()).body(err);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        String code = hasCause(ex, JsonParseException.class) ? "MALFORMED_JSON" : "INVALID_REQUEST_BODY";
        String message = "MALFORMED_JSON".equals(code)
                ? "Yêu cầu JSON không hợp lệ"
                : "Nội dung yêu cầu không hợp lệ";

        Map<String, Object> details = requestBodyDetails(ex);

        log.warn("Request body unreadable at path={} code={}", req.getRequestURI(), code);

        return buildError(HttpStatus.BAD_REQUEST, code, message, req, details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String parameter = ex.getName() != null ? ex.getName() : "parameter";
        Map<String, Object> details = Map.of(
                "fields", Map.of(parameter, typeMismatchMessage(ex.getRequiredType()))
        );

        log.warn("Type mismatch at path={} parameter={}", req.getRequestURI(), parameter);

        return buildError(
                HttpStatus.BAD_REQUEST,
                "TYPE_MISMATCH",
                "Tham số yêu cầu không hợp lệ",
                req,
                details
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
                                                                         HttpServletRequest req) {
        Map<String, Object> details = Map.of(
                "fields", Map.of(ex.getParameterName(), "Thiếu tham số bắt buộc")
        );

        log.warn("Missing request parameter at path={} parameter={}", req.getRequestURI(), ex.getParameterName());

        return buildError(
                HttpStatus.BAD_REQUEST,
                "MISSING_REQUEST_PARAMETER",
                "Thiếu tham số bắt buộc",
                req,
                details
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingRequestHeader(MissingRequestHeaderException ex, HttpServletRequest req) {
        Map<String, Object> details = Map.of(
                "headers", Map.of(ex.getHeaderName(), "Thiếu header bắt buộc")
        );

        log.warn("Missing request header at path={} header={}", req.getRequestURI(), ex.getHeaderName());

        return buildError(
                HttpStatus.BAD_REQUEST,
                "MISSING_REQUEST_HEADER",
                "Thiếu header bắt buộc",
                req,
                details
        );
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleBind(BindException ex, HttpServletRequest req) {
        Map<String, Object> details = bindingDetails(ex);

        log.warn("Bind failed at path={} details={}", req.getRequestURI(), details);

        return buildError(
                ErrorCode.VALIDATION_ERROR.getStatus(),
                ErrorCode.VALIDATION_ERROR.name(),
                ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                req,
                details
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest req) {
        Map<String, Object> details = new HashMap<>();
        if (ex.getMethod() != null) {
            details.put("method", ex.getMethod());
        }
        if (ex.getSupportedMethods() != null && ex.getSupportedMethods().length > 0) {
            details.put("supportedMethods", Arrays.asList(ex.getSupportedMethods()));
        }

        log.warn("Method not supported at path={} method={}", req.getRequestURI(), ex.getMethod());

        return buildError(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "Phương thức HTTP không được hỗ trợ",
                req,
                details
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                                HttpServletRequest req) {
        Map<String, Object> details = new HashMap<>();
        if (ex.getContentType() != null) {
            details.put("contentType", ex.getContentType().toString());
        }
        if (!ex.getSupportedMediaTypes().isEmpty()) {
            details.put("supportedMediaTypes", toStringList(ex.getSupportedMediaTypes()));
        }

        log.warn("Media type not supported at path={} contentType={}", req.getRequestURI(), ex.getContentType());

        return buildError(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "Content-Type không được hỗ trợ",
                req,
                details
        );
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                                                 HttpServletRequest req) {
        Map<String, Object> details = new HashMap<>();
        if (!ex.getSupportedMediaTypes().isEmpty()) {
            details.put("supportedMediaTypes", toStringList(ex.getSupportedMediaTypes()));
        }

        log.warn("Media type not acceptable at path={}", req.getRequestURI());

        return buildError(
                HttpStatus.NOT_ACCEPTABLE,
                "NOT_ACCEPTABLE_MEDIA_TYPE",
                "Header Accept không được hỗ trợ",
                req,
                details
        );
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(Exception ex, HttpServletRequest req) {
        log.warn("No API handler found at path={}", req.getRequestURI());

        return buildError(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                "Không tìm thấy API",
                req,
                null
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception at path={}", req.getRequestURI(), ex);

        ApiError err = ApiError.builder()
                               .success(false)
                               .code(ErrorCode.INTERNAL_ERROR.name())
                               .message(ErrorCode.INTERNAL_ERROR.getDefaultMessage())
                               .timestamp(Instant.now())
                               .status(ErrorCode.INTERNAL_ERROR.getStatus().value())
                               .path(req.getRequestURI())
                               .build();

        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus()).body(err);
    }

    private ResponseEntity<ApiError> buildError(HttpStatus status,
                                                String code,
                                                String message,
                                                HttpServletRequest req,
                                                Map<String, Object> details) {
        ApiError.ApiErrorBuilder builder = ApiError.builder()
                                                   .success(false)
                                                   .code(code)
                                                   .message(message)
                                                   .timestamp(Instant.now())
                                                   .status(status.value())
                                                   .path(req.getRequestURI());
        if (details != null && !details.isEmpty()) {
            builder.details(details);
        }

        return ResponseEntity.status(status).body(builder.build());
    }

    private Map<String, Object> requestBodyDetails(HttpMessageNotReadableException ex) {
        MismatchedInputException mismatch = findCause(ex, MismatchedInputException.class);
        if (mismatch == null) {
            return null;
        }

        String path = jsonPath(mismatch);
        if (path == null || path.isBlank()) {
            return null;
        }

        InvalidFormatException invalidFormat = findCause(ex, InvalidFormatException.class);
        Class<?> targetType = invalidFormat != null ? invalidFormat.getTargetType() : null;

        return Map.of(
                "fields", Map.of(path, typeMismatchMessage(targetType))
        );
    }

    private Map<String, Object> bindingDetails(BindException ex) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            String message = fieldError.isBindingFailure()
                    ? "Giá trị không đúng định dạng"
                    : fieldError.getDefaultMessage();
            fields.put(fieldError.getField(), message != null ? message : "Dữ liệu không hợp lệ");
        }

        List<String> errors = ex.getBindingResult()
                                .getGlobalErrors()
                                .stream()
                                .map(ObjectError::getDefaultMessage)
                                .filter(Objects::nonNull)
                                .toList();

        Map<String, Object> details = new HashMap<>();
        if (!fields.isEmpty()) {
            details.put("fields", fields);
        }
        if (!errors.isEmpty()) {
            details.put("errors", errors);
        }
        return details;
    }

    private String typeMismatchMessage(Class<?> requiredType) {
        if (requiredType == null) {
            return "Giá trị không đúng định dạng";
        }
        if (requiredType.isEnum()) {
            return "Giá trị không hợp lệ. Giá trị hợp lệ: " + Arrays.stream(requiredType.getEnumConstants())
                                                                    .map(String::valueOf)
                                                                    .collect(Collectors.joining(", "));
        }
        if (LocalDate.class.equals(requiredType)) {
            return "Ngày không đúng định dạng yyyy-MM-dd";
        }
        if (LocalDateTime.class.equals(requiredType)) {
            return "Ngày giờ không đúng định dạng yyyy-MM-dd'T'HH:mm:ss";
        }
        if (LocalTime.class.equals(requiredType)) {
            return "Thời gian không đúng định dạng HH:mm:ss";
        }
        if (isNumericType(requiredType)) {
            return "Giá trị phải là số hợp lệ";
        }
        if (Boolean.class.equals(requiredType) || boolean.class.equals(requiredType)) {
            return "Giá trị phải là true hoặc false";
        }
        return "Giá trị không đúng định dạng";
    }

    private boolean isNumericType(Class<?> type) {
        return Number.class.isAssignableFrom(type)
                || byte.class.equals(type)
                || short.class.equals(type)
                || int.class.equals(type)
                || long.class.equals(type)
                || float.class.equals(type)
                || double.class.equals(type);
    }

    private String jsonPath(MismatchedInputException ex) {
        if (ex.getPath() == null || ex.getPath().isEmpty()) {
            return null;
        }

        return ex.getPath()
                 .stream()
                 .map(this::jsonPathSegment)
                 .filter(segment -> segment != null && !segment.isBlank())
                 .collect(Collectors.joining("."))
                 .replace(".[", "[");
    }

    private String jsonPathSegment(JsonMappingException.Reference reference) {
        if (reference.getFieldName() != null) {
            return reference.getFieldName();
        }
        if (reference.getIndex() >= 0) {
            return "[" + reference.getIndex() + "]";
        }
        return null;
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        return findCause(throwable, type) != null;
    }

    private List<String> toStringList(List<?> values) {
        return values.stream()
                     .map(String::valueOf)
                     .toList();
    }
}
