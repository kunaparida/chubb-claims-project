import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { problemMessage } from '../../core/problem';
import { AuthShell } from '../../shared/auth-shell';
import { Icon } from '../../shared/icon';
import { Logo } from '../../shared/logo';

/** The backend seeds one officer and one manager for local use; claimants register themselves. */
const DEMO_PASSWORD = 'ChangeMe123!';
const DEMO_ACCOUNTS = [
  { label: 'Officer', email: 'officer@claims.local' },
  { label: 'Manager', email: 'manager@claims.local' },
];

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, AuthShell, Icon, Logo],
  templateUrl: './login.html',
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly demo = DEMO_ACCOUNTS;
  protected readonly demoPassword = DEMO_PASSWORD;
  protected readonly busy = signal(false);
  protected readonly showPassword = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected fill(email: string): void {
    this.form.setValue({ email, password: DEMO_PASSWORD });
    this.error.set(null);
  }

  protected submit(): void {
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: (res) => void this.router.navigateByUrl(this.auth.homeFor(res.role)),
      error: (err: unknown) => {
        this.busy.set(false);
        this.error.set(problemMessage(err, 'Sign-in failed.'));
      },
    });
  }
}
