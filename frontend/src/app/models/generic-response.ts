import { Link } from './link';
import { Meta } from './meta';

export interface GenericResponse<T> {
  meta: Meta;
  data: T;
  link: Link;
}
