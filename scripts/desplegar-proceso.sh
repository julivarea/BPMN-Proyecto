#!/usr/bin/env bash
# Despliega en el servidor el modelo BPMN y la regla DMN juntos, como un "app deployment" (.bar).
# Por la REST de procesos solo se puede subir un archivo y el DMN no llega al motor de decisiones.
set -euo pipefail
cd "$(dirname "$0")/.."

FLOWABLE_URL="${FLOWABLE_URL:-http://localhost:8080/flowable-ui}"
ADMIN="${FLOWABLE_ADMIN:-admin:test}"
PAQUETE=target/organizar-curso.bar

mkdir -p target
rm -f "$PAQUETE"
jar --create --file "$PAQUETE" -C proceso/src/main/resources/procesos .

estado=$(curl -s -o /dev/null -w '%{http_code}' -u "$ADMIN" -F "file=@$PAQUETE" \
  "$FLOWABLE_URL/app-api/app-repository/deployments")
if [[ "$estado" != 201 ]]; then
  echo "Error $estado al desplegar $PAQUETE (ver: docker compose logs flowable)" >&2
  exit 1
fi
echo "Desplegado $PAQUETE"
