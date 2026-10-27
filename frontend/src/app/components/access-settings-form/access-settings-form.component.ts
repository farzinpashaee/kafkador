import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AccessMode, AccessSettings } from '../../models';

/**
 * Who may use Kafkador: no sign-in, the admin account kept in Kafkador's database, or LDAP accounts. Used by first-run
 * setup and by Settings > Access, which own the saving; this edits the settings object it's given.
 */
@Component({
  selector: 'app-access-settings-form',
  imports: [CommonModule, FormsModule],
  templateUrl: './access-settings-form.component.html'
})
export class AccessSettingsFormComponent {

  @Input({ required: true }) settings!: AccessSettings;
  /** The admin username already saved (Settings): keeping it, a blank password keeps the current one. */
  @Input() savedUsername = '';
  /** Keeps the field ids unique when the form is on a page twice. */
  @Input() idPrefix = 'access';

  confirmPassword = '';
  /** LDAP: find the user from a DN pattern, or search for them. */
  ldapFind: 'pattern' | 'search' = 'search';

  readonly modes: { mode: AccessMode; title: string; text: string }[] = [
    { mode: 'DATABASE', title: 'Sign in with a Kafkador account',
      text: 'One admin account, kept in Kafkador\'s database with its password hashed.' },
    { mode: 'LDAP', title: 'Sign in with LDAP',
      text: 'Accounts from your LDAP directory or Active Directory.' },
    { mode: 'NONE', title: 'No sign-in',
      text: 'Anyone who can reach Kafkador can use all of it. Only for a machine nobody else can reach.' }
  ];

  ngOnChanges() {
    this.ldapFind = this.settings?.ldap?.userDnPattern ? 'pattern' : 'search';
  }

  get passwordOptional(): boolean {
    return !!this.savedUsername && this.settings.username.trim() === this.savedUsername;
  }

  setLdapFind(find: 'pattern' | 'search') {
    this.ldapFind = find;
    if (find === 'pattern') {
      this.settings.ldap.userSearchBase = '';
      this.settings.ldap.userSearchFilter = '';
    } else {
      this.settings.ldap.userDnPattern = '';
    }
  }

  /** What to fix before saving, or null. The server checks the rest. */
  problem(): string | null {
    if (!this.settings.mode) return 'Choose who may use Kafkador.';
    if (this.settings.mode === 'DATABASE') {
      if (!this.settings.username.trim()) return 'Enter a username for the admin account.';
      if (!this.settings.password && !this.passwordOptional) return 'Choose a password for the admin account.';
      if (this.settings.password !== this.confirmPassword) return 'Passwords do not match.';
    }
    if (this.settings.mode === 'LDAP') {
      if (!this.settings.ldap.url.trim()) return 'Enter the LDAP URL.';
      if (!this.settings.testUsername.trim() || !this.settings.testPassword) {
        return 'Enter an LDAP username and password to check the settings with.';
      }
    }
    return null;
  }

}
