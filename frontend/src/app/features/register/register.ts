import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { fieldErrors, problemMessage } from '../../core/problem';
import { AuthShell } from '../../shared/auth-shell';
import { Icon } from '../../shared/icon';
import { Logo } from '../../shared/logo';

/** Public registration always creates a CLAIMANT (officers and managers are provisioned by the backend). */
@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, AuthShell, Icon, Logo],
  templateUrl: './register.html',
})
export class Register {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly busy = signal(false);
  protected readonly showPassword = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly serverErrors = signal<Record<string, string>>({});

  protected readonly form = this.fb.nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });

  protected problem(field: string): string | undefined {
    return this.serverErrors()[field];
  }

  protected submit(): void {
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.serverErrors.set({});
    const { fullName, email, password } = this.form.getRawValue();
    this.auth.register(fullName.trim(), email.trim(), password).subscribe({
      next: (res) => void this.router.navigateByUrl(this.auth.homeFor(res.role)),
      error: (err: unknown) => {
        this.busy.set(false);
        this.serverErrors.set(fieldErrors(err));
        this.error.set(problemMessage(err, 'Registration failed.'));
      },
    });
  }
}
