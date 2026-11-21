#!/usr/bin/env bash
# Crea en Flowable los cinco usuarios del personal y los grupos (enunciado 2 y 3.3), y da a sus miembros
# los privilegios para usar la interfaz de Flowable (access-task) y su API REST (access-rest-api).
# Se puede correr más de una vez: lo que ya existe se deja como está.
set -euo pipefail
cd "$(dirname "$0")/.."

FLOWABLE_URL="${FLOWABLE_URL:-http://localhost:8080/flowable-ui}"
ADMIN="${FLOWABLE_ADMIN:-admin:test}"
CLAVE_DEMO="${CLAVE_DEMO:-clave123}"
API_IDENTIDADES="$FLOWABLE_URL/process-api/identity"

publicar() { # publicar <ruta> <json>: 201 creado, 409 ya existía
  local estado
  estado=$(curl -s -o /dev/null -w '%{http_code}' -u "$ADMIN" -H 'Content-Type: application/json' \
    -X POST "$API_IDENTIDADES/$1" -d "$2")
  if [[ "$estado" != 201 && "$estado" != 409 ]]; then
    echo "Error $estado en POST $1" >&2
    exit 1
  fi
}

crear_grupo() { # crear_grupo <id> <nombre>
  publicar groups "{\"id\":\"$1\",\"name\":\"$2\",\"type\":\"assignment\"}"
}

crear_usuario() { # crear_usuario <id> <nombre> <apellido> <grupo>
  publicar users "{\"id\":\"$1\",\"firstName\":\"$2\",\"lastName\":\"$3\",\"email\":\"$1@institucion.test\",\"password\":\"$CLAVE_DEMO\"}"
  publicar "groups/$4/members" "{\"userId\":\"$1\"}"
}

# El nombre va con la o con tilde escapada (\u00f3): en Git Bash de Windows curl recibe los argumentos en otra
# codificación y Flowable rechaza el JSON con un 400.
crear_grupo administracion 'Administraci\u00f3n'
crear_grupo docentes "Docentes"

crear_usuario jefe_admin     Jefe Administrativo administracion
crear_usuario administrativo Personal Administrativo administracion
crear_usuario jefe_docente   Jefe Docente docentes
crear_usuario docente1       Docente Uno docentes
crear_usuario docente2       Docente Dos docentes

# Flowable 6.8 no expone los privilegios por REST: se asignan en su BD a cada miembro de los grupos.
# Se asignan por usuario y no por grupo porque la UI de Flowable solo muestra las apps según los del usuario.
docker compose exec -T postgres psql -q -U "${POSTGRES_USER:-flowable}" -d flowable -v ON_ERROR_STOP=1 <<'SQL'
INSERT INTO act_id_priv_mapping (id_, priv_id_, user_id_)
SELECT md5(privilegio.id_ || miembro.user_id_), privilegio.id_, miembro.user_id_
FROM act_id_priv privilegio
JOIN act_id_membership miembro ON miembro.group_id_ IN ('administracion', 'docentes')
WHERE privilegio.name_ IN ('access-task', 'access-rest-api')
  AND NOT EXISTS (SELECT 1 FROM act_id_priv_mapping asignado
                  WHERE asignado.priv_id_ = privilegio.id_ AND asignado.user_id_ = miembro.user_id_);
SQL

echo "Usuarios y grupos listos. Contraseña de todos los usuarios de prueba: $CLAVE_DEMO"
