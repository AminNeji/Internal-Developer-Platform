import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CreateEnvironmentReq } from '../../core/environment.model';
import { EnvironmentService } from '../../core/environment.service';

type ServiceForm = FormGroup<{
  serviceName: FormControl<string>;
  image: FormControl<string>;
  port: FormControl<number>;
  path: FormControl<string>;
}>;

@Component({
  selector: 'app-environment-create',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './environment-create.component.html',
  styleUrl: './environment-create.component.scss',
})
export class EnvironmentCreateComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(EnvironmentService);
  private readonly router = inject(Router);

  readonly types = ['dev', 'staging', 'preview'];

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    team: ['', [Validators.required, Validators.maxLength(40)]],
    type: ['dev', [Validators.required]],
    services: this.fb.array<ServiceForm>([this.newService()]),
  });

  private readonly formValue = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  // mirrors generateEnvId() in EnvironmentService.java - the suffix is added server-side
  readonly envIdPreview = computed(() => {
    const value = this.formValue();
    const base = `${value.type ?? ''}-${value.team ?? ''}`
      .toLowerCase()
      .replace(/[^a-z0-9-]/g, '-');
    return `${base}-`;
  });

  get services(): FormArray<ServiceForm> {
    return this.form.controls.services;
  }

  newService(): ServiceForm {
    return this.fb.nonNullable.group({
      serviceName: ['', [Validators.required, Validators.pattern(/^[a-z0-9-]+$/)]],
      image: ['', [Validators.required]],
      port: [8080, [Validators.required, Validators.min(1), Validators.max(65535)]],
      path: ['/', [Validators.required, Validators.pattern(/^\/.*/)]],
    });
  }

  addService(): void {
    this.services.push(this.newService());
  }

  removeService(index: number): void {
    this.services.removeAt(index);
  }

  invalid(control: AbstractControl | null): boolean {
    return !!control && control.invalid && (control.touched || control.dirty);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    const payload = this.form.getRawValue() as CreateEnvironmentReq;

    this.api.create(payload).subscribe({
      next: env => this.router.navigate(['/environments', env.envId]),
      error: () => {
        this.submitting.set(false);
        this.error.set(
          'The platform could not create this environment. Check the backend logs - the Git push or the ArgoCD call may have failed.',
        );
      },
    });
  }
}
