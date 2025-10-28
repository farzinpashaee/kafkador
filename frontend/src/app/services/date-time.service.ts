import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class DateTimeService {

  formatDateTimeFromNow(dateString: string): string {
    const date = new Date(dateString);
    const now = new Date();

    const diffMs = now.getTime() - date.getTime();
    const diffMinutes = Math.floor(diffMs / (1000 * 60));
    const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

    // Case 1: Today → X min/hours/days ago
    if (diffDays < 1) {
      if (diffMinutes < 1) return 'Just now';
      if (diffMinutes < 60) return `${diffMinutes} min ago`;
      return `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`;
    }

    // Case 2: Within last few days
    if (diffDays < 7) return `${diffDays} day${diffDays > 1 ? 's' : ''} ago`;

    // Case 3: Older → show formatted date (e.g. 27th Sep)
    const day = date.getDate();
    const month = date.toLocaleString('default', { month: 'short' });
    const suffix =
      day % 10 === 1 && day !== 11 ? 'st'
      : day % 10 === 2 && day !== 12 ? 'nd'
      : day % 10 === 3 && day !== 13 ? 'rd'
      : 'th';

    return `${day}${suffix} ${month}`;
  }

}
