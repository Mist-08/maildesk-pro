package com.mycompany.maildesk.config;

import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

/** Errores sin detalles internos: mensajes genéricos para el usuario y detalle solo en el registro. */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final GlobalModelAdvice globals;

    public GlobalExceptionHandler(GlobalModelAdvice globals) {
        this.globals = globals;
    }

    private ModelAndView view(String name, HttpStatus status) {
        ModelAndView mav = new ModelAndView(name);
        mav.setStatus(status);
        globals.populate(mav.getModel());
        return mav;
    }

    @ExceptionHandler(NotFoundException.class)
    public Object notFound(NotFoundException e, HttpServletRequest request) {
        if (isApi(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No encontrado"));
        }
        return view("error/404", HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BusinessException.class)
    public Object business(BusinessException e, HttpServletRequest request) {
        if (isApi(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
        ModelAndView mav = view("error/business", HttpStatus.BAD_REQUEST);
        mav.addObject("message", e.getMessage());
        return mav;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object tooLarge(MaxUploadSizeExceededException e, HttpServletRequest request) {
        if (isApi(request)) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error", "Archivo demasiado grande"));
        }
        ModelAndView mav = view("error/business", HttpStatus.PAYLOAD_TOO_LARGE);
        mav.addObject("message", "El archivo supera el tamaño máximo permitido.");
        return mav;
    }

    @ExceptionHandler(Exception.class)
    public Object unexpected(Exception e, HttpServletRequest request) throws Exception {
        if (e instanceof org.springframework.security.access.AccessDeniedException) {
            throw e; // Lo gestiona Spring Security (403).
        }
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), e);
        if (isApi(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error interno"));
        }
        return view("error/500", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static boolean isApi(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }
}
