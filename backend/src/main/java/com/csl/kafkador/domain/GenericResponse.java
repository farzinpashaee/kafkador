package com.csl.kafkador.domain;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Date;

@Data
@Accessors(chain = true)
public class GenericResponse<T> {

    Meta meta;
    T data;
    Link link;
    Error error;

    @Data
    @Accessors(chain = true)
    public static class Meta {
    }

    @Data
    @Accessors(chain = true)
    public static class Link {

    }

    @Data
    @Accessors(chain = true)
    public static class Error {
        private String code;
        private String message;
        private Date datetime;
    }

    public static class Builder<T> {

        Meta meta;
        T data;
        Link link;
        Error error;

        public Builder<T> meta( Meta meta ){
            this.meta = meta;
            return this;
        }

        public Builder<T> data( T data ){
            this.data = data;
            return this;
        }

        public Builder<T> code( String code ){
            if( error == null ) error = new Error();
            error.setCode(code);
            return this;
        }

        public Builder<T> message( String message ){
            if( error == null ) error = new Error();
            error.setMessage(message);
            return this;
        }

        public Builder<T> link(){
            return this;
        }

        public ResponseEntity<GenericResponse<T>> success( HttpStatus httpStatus ){
            GenericResponse<T> response = new GenericResponse<T>()
                    .setMeta(this.meta)
                    .setData(this.data)
                    .setLink(this.link);
            return ResponseEntity.ok(response);
        }

        public ResponseEntity<GenericResponse<T>> failed( HttpStatus httpStatus ){
            this.error.setDatetime(new Date());
            GenericResponse<T> response = new GenericResponse<T>()
                    .setError(error);
            return new ResponseEntity<>(response,httpStatus);
        }


    }



}
