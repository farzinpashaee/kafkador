package com.csl.kafkador.exception;

/** The cluster rejected an ACL binding as malformed, e.g. a principal without its "User:" type prefix. */
public class InvalidAclBindingException extends Exception {

    public InvalidAclBindingException(String message) {
        super(message);
    }

}
