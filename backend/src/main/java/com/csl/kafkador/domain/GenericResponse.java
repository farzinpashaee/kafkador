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

    @Data
    @Accessors(chain = true)
    public static class Meta {
        private String error;
        private String message;
        private Date datetime;

    }

    @Data
    @Accessors(chain = true)
    public static class Link {

    }

    public static class Builder<T> {

        Meta meta;
        T data;
        Link link;
        String error;
        String message;

        public Builder<T> meta( Meta meta ){
            this.meta = meta;
            return this;
        }

        public Builder<T> data( T data ){
            this.data = data;
            return this;
        }

        public Builder<T> error( String error ){
            if( meta == null ) meta = new Meta();
            meta.setError(error);
            return this;
        }

        public Builder<T> message( String message ){
            if( meta == null ) meta = new Meta();
            meta.setMessage(message);
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
            this.meta.setDatetime(new Date());
            GenericResponse<T> response = new GenericResponse<T>()
                    .setMeta(this.meta)
                    .setLink(this.link);
            return new ResponseEntity<>(response,httpStatus);
        }


    }



}
