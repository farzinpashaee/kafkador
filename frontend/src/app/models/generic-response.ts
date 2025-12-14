import { Link } from './link';
import { Meta } from './meta';
import { Error } from './error';

export interface GenericResponse<T> {
  meta: Meta;
  data: T;
  link: Link;
  error: Error;
}
