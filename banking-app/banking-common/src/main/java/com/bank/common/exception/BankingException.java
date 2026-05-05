package com.bank.common.exception;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;

public class BankingException extends RuntimeException{

	private final String errorCode;
	
	private final HttpStatus httpStatus;
	
	private final String errorId;
	
	private final LocalDateTime timestamp;
	
	private final boolean exposeDetails;
	
	public BankingException(String message, String errorCode, HttpStatus httpStatus) {
		
		super(message);
		this.errorCode = errorCode;
		this.httpStatus = httpStatus;
		this.errorId = UUID.randomUUID().toString();
		this.timestamp = LocalDateTime.now();
		this.exposeDetails = true;
	}
	
    public BankingException(String message, String errorCode,
            HttpStatus httpStatus, Throwable cause) {
		super(message, cause);
		this.errorCode     = errorCode;
		this.httpStatus    = httpStatus;
		this.errorId       = UUID.randomUUID().toString();
		this.timestamp     = LocalDateTime.now();
		this.exposeDetails = true;
    }
    public BankingException(String message, String errorCode,
            HttpStatus httpStatus, boolean exposeDetails) {
		super(message);
		this.errorCode     = errorCode;
		this.httpStatus    = httpStatus;
		this.errorId       = UUID.randomUUID().toString();
		this.timestamp     = LocalDateTime.now();
		this.exposeDetails = exposeDetails;
	}
    public String getErrorCode() {
        return errorCode;
    }
 
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
 
    public String getErrorId() {
        return errorId;
    }
 
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
 
    public boolean isExposeDetails() {
        return exposeDetails;
    }
 
    public int getStatusCode() {
        return httpStatus.value();
    }
    
    @Override
    public String toString() {
        return "BankingException{"
            + "errorId='"   + errorId   + '\''
            + ", errorCode='" + errorCode + '\''
            + ", status="   + httpStatus.value()
            + ", message='" + getMessage() + '\''
            + ", timestamp=" + timestamp
            + '}';
    }
}
