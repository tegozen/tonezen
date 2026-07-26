"""Idempotent GlitchTip bootstrap: admin, org, teams, projects, DSN keys, CI token."""

from __future__ import annotations

import os
import uuid

from django.contrib.auth import get_user_model
from django.db import connection

from apps.api_tokens.models import APIToken
from apps.organizations_ext.models import Organization
from apps.projects.models import Project, ProjectKey
from apps.teams.models import Team

# Fixed project IDs (must match Android/Desktop client DSN builders).
ANDROID_PROJECT_ID = 1
DESKTOP_PROJECT_ID = 2


def _require(name: str) -> str:
    value = (os.environ.get(name) or "").strip()
    if not value:
        raise SystemExit(f"{name} is required")
    return value


def _parse_public_key(raw: str) -> uuid.UUID:
    cleaned = raw.strip().replace("-", "")
    if len(cleaned) != 32:
        raise SystemExit(
            f"Public key must be 32 hex chars (got {len(cleaned)}): {raw!r}"
        )
    return uuid.UUID(hex=cleaned)


def _ensure_admin(email: str, password: str):
    User = get_user_model()
    user = User.objects.filter(email__iexact=email).first()
    if user is None:
        user = User.objects.create_superuser(email=email, password=password)
        print(f"Created GlitchTip admin: {email}")
    else:
        user.is_staff = True
        user.is_superuser = True
        user.is_active = True
        user.set_password(password)
        user.save()
        print(f"Updated GlitchTip admin: {email}")
    return user


def _ensure_org(user, slug: str = "tonezen", name: str = "Tonezen"):
    org = Organization.objects.filter(slug=slug).first()
    if org is None:
        org = Organization.objects.create(name=name, slug=slug)
        print(f"Created organization: {slug}")
    else:
        print(f"Organization already exists: {slug}")
    org_user = org.add_user(user)
    return org, org_user


def _ensure_team(org: Organization, org_user, slug: str = "tonezen") -> Team:
    team, created = Team.objects.get_or_create(organization=org, slug=slug)
    team.members.add(org_user)
    print(f"{'Created' if created else 'Found'} team: {slug}")
    return team


def _reset_project_id_sequence() -> None:
    with connection.cursor() as cursor:
        cursor.execute(
            """
            SELECT setval(
              pg_get_serial_sequence('projects_project', 'id'),
              COALESCE((SELECT MAX(id) FROM projects_project), 1)
            )
            """
        )


def _ensure_project(
    *,
    org: Organization,
    team: Team,
    project_id: int,
    name: str,
    platform: str,
    public_key: uuid.UUID,
) -> Project:
    project = Project.objects.filter(id=project_id).first()
    if project is None:
        by_slug = Project.objects.filter(organization=org, name=name).first()
        if by_slug is not None and by_slug.id != project_id:
            raise SystemExit(
                f"Project {name!r} exists as id={by_slug.id}, expected id={project_id}. "
                "Reset glitchtip-postgres volume or keep fixed project ids (android=1, desktop=2)."
            )
        # Explicit pk: Project.save() skips auto ProjectKey creation when pk is set.
        project = Project(
            id=project_id,
            name=name,
            organization=org,
            platform=platform,
        )
        project.save()
        print(f"Created project {name!r} id={project_id}")
        _reset_project_id_sequence()
    else:
        project.name = name
        project.platform = platform
        project.organization = org
        project.save(update_fields=["name", "platform", "organization"])
        print(f"Found project {name!r} id={project_id}")

    team.projects.add(project)

    keys = list(ProjectKey.objects.filter(project=project).order_by("id"))
    if not keys:
        key = ProjectKey(project=project, name="default")
        key.save()
        ProjectKey.objects.filter(pk=key.pk).update(public_key=public_key)
        print(f"Created project key for {name!r}")
    else:
        primary = keys[0]
        ProjectKey.objects.filter(pk=primary.pk).update(
            public_key=public_key,
            name="default",
            is_active=True,
        )
        for extra in keys[1:]:
            extra.delete()
        print(f"Updated project key for {name!r}")

    return project


def _ensure_auth_token(user, token_value: str) -> None:
    existing = APIToken.objects.filter(token=token_value).first()
    if existing is None:
        APIToken.objects.filter(user=user, label="tonezen-ci").delete()
        token = APIToken(user=user, label="tonezen-ci")
        token.save()
        APIToken.objects.filter(pk=token.pk).update(token=token_value)
        token = APIToken.objects.get(pk=token.pk)
    else:
        token = existing
        if token.user_id != user.id:
            token.user = user
            token.save(update_fields=["user"])

    token.add_permissions(
        [
            "project:read",
            "project:write",
            "project:admin",
            "project:releases",
            "org:read",
            "org:write",
            "event:read",
            "event:write",
            "event:admin",
        ]
    )
    print("Ensured GlitchTip CI auth token (tonezen-ci)")


def main() -> None:
    # Reuse Tonezen admin credentials (same as GoTrue seed).
    email = (os.environ.get("ADMIN_EMAIL") or "admin@tonezen.local").strip()
    password = _require("ADMIN_PASSWORD")
    android_key = _parse_public_key(_require("GLITCHTIP_ANDROID_PUBLIC_KEY"))
    desktop_key = _parse_public_key(_require("GLITCHTIP_DESKTOP_PUBLIC_KEY"))
    auth_token = _require("GLITCHTIP_AUTH_TOKEN")

    user = _ensure_admin(email, password)
    org, org_user = _ensure_org(user)
    team = _ensure_team(org, org_user)
    _ensure_project(
        org=org,
        team=team,
        project_id=ANDROID_PROJECT_ID,
        name="tonezen-android",
        platform="android",
        public_key=android_key,
    )
    _ensure_project(
        org=org,
        team=team,
        project_id=DESKTOP_PROJECT_ID,
        name="tonezen-desktop",
        platform="javascript-electron",
        public_key=desktop_key,
    )
    _ensure_auth_token(user, auth_token)
    print("GlitchTip seed complete")


main()
