package com.csl.kafkador.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Data
@Accessors(chain = true)
public class GenericResponse<T> {

    Meta meta;
    T data;
    Link link;

    public static class Meta {

    }

    public static class Link {

    }

    public static class Builder<T> {

        Meta meta;
        T data;
        Link link;
        HttpStatus httpStatus;

        public Builder meta(){
            return this;
        }

        public Builder data( T data ){
            this.data = data;
            return this;
        }

        public Builder link(){
            return this;
        }

        public ResponseEntity<GenericResponse<T>> success(HttpStatus httpStatus ){
            GenericResponse<T> response = new GenericResponse<T>()
                    .setMeta(this.meta)
                    .setData(this.data)
                    .setLink(this.link);
            return ResponseEntity.ok(response);
        }

        public ResponseEntity<GenericResponse<T>> error( HttpStatus httpStatus ){
            GenericResponse<T> response = new GenericResponse<T>()
                    .setMeta(this.meta)
                    .setLink(this.link);
            return new ResponseEntity<>(response,httpStatus);
        }


    }



}
