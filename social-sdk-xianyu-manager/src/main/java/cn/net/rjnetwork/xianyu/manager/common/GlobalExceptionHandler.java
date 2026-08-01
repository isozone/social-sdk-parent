package cn.net.rjnetwork.xianyu.manager.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBadRequest(IllegalArgumentException e) {
        logger.warn("Bad Request: {}", e.getMessage());
        return ApiResponse.fail("INVALID_ARGUMENT", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationExceptions(MethodArgumentNotValidException e) {
        String errors = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ApiResponse.fail("VALIDATION_ERROR", errors);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDenied(AccessDeniedException e) {
        logger.warn("Access Denied: {}", e.getMessage());
        return ApiResponse.fail("ACCESS_DENIED", "Access denied");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResourceFound(NoResourceFoundException e, HttpServletRequest request) {
        // Spring 6.x 静态资源处理器找不到文件时抛 NoResourceFoundException（而非走 /error），
        // 若直接落到底部 Exception 兜底会返回 500 INTERNAL_ERROR。
        // 这里复刻 SpaErrorController 的判定：无扩展名、非接口前缀的路径视为 SPA 前端路由，
        // 刷新时转发回 index.html 交给 Vue Router 接管；其余（API/真实资源 404）返回 JSON 404。
        String uri = request.getRequestURI();
        boolean isSpaRoute = uri != null
                && !uri.startsWith("/api")
                && !uri.startsWith("/openapi")
                && !uri.startsWith("/ws")
                && !uri.startsWith("/v3")
                && !uri.startsWith("/swagger-ui")
                && !uri.contains(".");

        if (isSpaRoute) {
            return new ModelAndView("forward:/index.html");
        }
        logger.warn("Resource not found: {}", uri);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("NOT_FOUND", "Resource not found: " + uri));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleGeneralException(Exception e) {
        logger.error("Unexpected error", e);
        return ApiResponse.fail("INTERNAL_ERROR", "An unexpected error occurred");
    }
}
