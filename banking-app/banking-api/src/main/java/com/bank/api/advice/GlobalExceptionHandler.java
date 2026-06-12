package com.bank.api.advice;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentTypeMismatchException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.bank.common.dto.ApiResponse;
import com.bank.common.dto.ApiResponse.ApiError;
import com.bank.common.exception.BankingException;
import com.bank.common.exception.InsufficientFundsException;
import com.bank.common.exception.UnauthorizedOperationException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	// 422 unprocessable-entity
	@ExceptionHandler(InsufficientFundsException.class)
	public ResponseEntity<ApiResponse<?>> handleInsufficientFunds(
			InsufficientFundsException ex,
			HttpServletRequest request
			) {
		
        log.warn("[API] Fonds insuffisants — path={} accountId={} requested={} available={}",
                request.getRequestURI(),
                ex.getAccountId(),
                ex.getRequestedAmount(),
                ex.getAvailableBalance());
        
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
        		ApiResponse.unprocessable(ex.getMessage(), List.of(ApiError.of(ex.getErrorCode(), ex.getMessage()))));
		
	}
	
	//403 forbiden
	@ExceptionHandler(UnauthorizedOperationException.class)
	public ResponseEntity<ApiResponse<?>> handleUnauthorizedOperation(
			UnauthorizedOperationException ex,
			HttpServletRequest request)
	{
		log.warn("[API] Opération non autorisée — path={} operation={} subject={}",
                request.getRequestURI(),
                ex.getOperation(),
                ex.getSubjectId());
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
				ApiResponse.forbidden(ex.getMessage()));
	}
	
	@ExceptionHandler(BankingException.class)
	public ResponseEntity<ApiResponse<?>> handlerBankingException(
			BankingException ex,
			HttpServletRequest request
			)
	{
		if (ex.getHttpStatus().is5xxServerError()) {
            log.error("[API] Erreur métier — errorId={} path={} code={} message={}",
                    ex.getErrorId(), request.getRequestURI(),
                    ex.getErrorCode(), ex.getMessage());

		}
		else
		{
            log.warn("[API] Erreur métier — errorId={} path={} code={} status={}",
                    ex.getErrorId(), request.getRequestURI(),
                    ex.getErrorCode(), ex.getStatusCode());

		}
		
		String message = ex.isExposeDetails() ? ex.getMessage() : "Une erreur est survenue :" + ex.getErrorId();
		
		return ResponseEntity.status(ex.getHttpStatus()).body(
				ApiResponse.error(ex.getStatusCode(), message, List.of(ApiError.of(ex.getErrorCode(), message))));
	}
	//400
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<?>> handleValidation(
			MethodArgumentNotValidException ex,
			HttpServletRequest request){
		
		List<ApiError> errors = ex.getBindingResult().getAllErrors().stream().map(error -> {
					
				if (error instanceof FieldError fe) {
					return ApiError.field(fe.getField(), "VALIDATION_"+fe.getField().toUpperCase(), fe.getDefaultMessage());
				}
					return ApiError.of("VALIDATION_ERROR",error.getDefaultMessage());
				}).collect(Collectors.toList());
		
        log.debug("[API] Validation échouée — path={} errors={}",
                request.getRequestURI(), errors.size());
        return ResponseEntity.badRequest()
                .body(ApiResponse.badRequest(
                    errors.size() + " erreur(s) de validation", errors));

	}
	/**
     * Gère les violations de contraintes sur les paramètres de requête
     * (@RequestParam, @PathVariable annotés avec @Valid).
     */

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {
 
        List<ApiError> errors = ex.getConstraintViolations()
            .stream()
            .map((ConstraintViolation<?> cv) -> {
                String path    = cv.getPropertyPath().toString();
                String field   = path.contains(".")
                    ? path.substring(path.lastIndexOf('.') + 1)
                    : path;
                return ApiError.field(field, "CONSTRAINT_VIOLATION",
                                      cv.getMessage());
            })
            .collect(Collectors.toList());
 
        log.debug("[API] Contrainte violée — path={} violations={}",
                  request.getRequestURI(), errors.size());
 
        return ResponseEntity.badRequest()
            .body(ApiResponse.badRequest("Paramètre(s) invalide(s)", errors));
    }
    /**
     * Gère les paramètres requis manquants (@RequestParam required=true).
     */

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingParam(MissingServletRequestParameterException ex)
    {
    	return ResponseEntity.badRequest().body(ApiResponse.badRequest("Parametre requis" + ex.getParameterName(),
    			List.of(ApiError.field(ex.getParameterName(), "MISSING_PARAMETER",  "Le paramètre '" + ex.getParameterName() + "' est obligatoire"))));
    }
    /**
     * Gère les erreurs de type sur les paramètres (ex : UUID mal formé dans @PathVariable).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        String message = "Paramètre de requête invalide : " + ex.getMessage();

        log.debug("[API] Type mismatch — {}", ex.getMessage());

        return ResponseEntity.badRequest()
            .body(ApiResponse.badRequest(
                message,
                List.of(ApiError.field(
                    "request",
                    "TYPE_MISMATCH",
                    message
                ))
            ));
    }
    /**
     * Gère les corps de requête illisibles (JSON malformé, type inconnu).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
 
        log.debug("[API] Corps illisible — path={}", request.getRequestURI());
 
        return ResponseEntity.badRequest()
            .body(ApiResponse.badRequest(
                "Corps de la requête invalide ou malformé.",
                List.of(ApiError.of("INVALID_REQUEST_BODY",
                    "Le corps JSON est manquant ou malformé"))
            ));
    }
    
    /*
     * Gère les IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArgument(
    		IllegalArgumentException ex,
    		HttpServletRequest request) {
    	
    	log.warn("Api argument illegal path={} message={}",
    			                request.getRequestURI(), ex.getMessage());
    	
    	return ResponseEntity.badRequest().body(ApiResponse.badRequest(ex.getMessage(), List.of(ApiError.of("INVALID_ARGUMENT", ex.getMessage()))));
    	
    }
    /**
     * Gère les accès refusés par Spring Security (@PreAuthorize, etc.).
     * 403 Forbidden.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {
 
        log.warn("[API] Accès refusé Spring Security — path={}",
                 request.getRequestURI());
 
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.forbidden(
                "Accès refusé. Vous ne disposez pas des droits nécessaires."));
    }
    /**
     * Gère les erreurs d'authentification Spring Security.
     * 401 Unauthorized.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<?>> handleAuthentication(
            AuthenticationException ex,
            HttpServletRequest request) {
 
        log.warn("[API] Authentification échouée — path={}", request.getRequestURI());
 
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.unauthorized(
                "Authentification requise. Veuillez vous connecter."));
    }
    /*
     * Gère les routes inexistantes 404 not found
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNotFound(
            NoHandlerFoundException ex) {
 
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.notFound(
                "Endpoint introuvable : " + ex.getHttpMethod()
                + " " + ex.getRequestURL()));
    }
    
    /*
     * Gère les méthodes HTTP inexistantes 405
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex){
    	
    	return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.error(405,"Méthode HTTP non supportée : " + ex.getMethod()
        + ". Méthode(s) acceptée(s) : "
        + (ex.getSupportedMethods() != null
            ? String.join(", ", ex.getSupportedMethods())
            : "N/A")));
    }
    /**
     * Gère les Content-Type non supportés (ex : XML envoyé sur un endpoint JSON-only).
     * 415 Unsupported Media Type.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex){
    	
    	return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(ApiResponse.error(415, "unsupported media type " +ex.getContentType()));
    }
    /**
     * Gère les conflits de verrou optimiste JPA (@Version).
     * 409 Conflict — l'entité a été modifiée entre la lecture et la sauvegarde.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<?>> handleOptimisticLock(
            OptimisticLockingFailureException ex,
            HttpServletRequest request) {
 
        log.warn("[API] Conflit de version (optimistic lock) — path={}",
                 request.getRequestURI());
 
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.conflict(
                "Conflit de modification simultanée. " +
                "Veuillez réessayer après avoir rechargé les données."));
    }
    /**
     * Gère les violations de contraintes base de données (unicité, FK, etc.).
     * 409 Conflict.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleDataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {
 
        log.error("[API] Violation contrainte BDD — path={} cause={}",
                  request.getRequestURI(),
                  ex.getMostSpecificCause().getMessage());
 
        // Ne jamais exposer le message SQL au client
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.conflict(
                "Opération impossible : une contrainte d'intégrité a été violée. " +
                "Cette ressource existe peut-être déjà."));
    }
    /**
     * Intercepte toute exception non couverte par les handlers précédents.
     * 500 Internal Server Error.
     *
     * 
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGenericException(
            Exception ex,
            HttpServletRequest request) {
 
        log.error("[API] Erreur interne non gérée — path={} exception={} message={}",
                  request.getRequestURI(),
                  ex.getClass().getSimpleName(),
                  ex.getMessage(), ex);
 
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.internalError(
                "Une erreur interne est survenue. " +
                "Notre équipe a été notifiée. Réessayez ultérieurement."));
    }
	
}
