#### **CUENTAS**



**jefe\_admin, administrativo, jefe\_docente, docente1, docente2**

**clave123**



#### **RESETEAR Y LEVANTAR TODO DE CERO**



docker compose down -v # borra contenedores, instancias y ambas BDs

./mvnw -pl proceso clean package # compila proceso/target/proceso.jar para Flowable

docker compose up -d # levanta PostgreSQL y Flowable 6.8

until curl -sf -u admin:test http://localhost:8080/flowable-ui/process-api/repository/deployments >/dev/null; do sleep 5; done # espera a que Flowable responda

bash scripts/setup-identidades.sh # crea los 5 usuarios, 2 grupos y privilegios

bash scripts/desplegar-proceso.sh # despliega organizarCurso.bpmn20.xml y viabilidad.dmn



#### **LEVANTAR APPS (UN COMANDO POR TERMINAL DISTINTA)**



**PLAZO\_RESPUESTA=PT30M ./mvnw -pl web spring-boot:run # web en http://localhost:8081 con plazo largo**

**./mvnw -pl app-comunicaciones compile exec:java # app de escritorio de comunicaciones**

**./mvnw -pl app-materiales compile exec:java # app de escritorio de materiales (entrar con administrativo / clave123)**





#### **VER LOGS EN TIEMPO REAL (EN UNA TERMINAL APARTE)**



**docker compose logs -f --tail=100 flowable 2>\&1 | grep --line-buffered 'a\\.e\\.u\\.g\\.p\\.'**



#### **ESCENARIOS**



* Escenario 3 (rechazo de una propuesta)

&#x09;Se hace manualmente creando la solicitud desde la web, rechazandola en la app de comunicaciones y con el jefe\_admin archivándola



* Escenario 4 (cierre por falta de respuesta luego de las dos notificaciones).

&#x09;**python scripts/escenarios/preparar\_escenario\_4.py**   # Crea una instancia que queda esperando una respuesta de la empresa; dejar que haga el timeout solo en 1.5 min.



* Escenarios varios con cancelación antes del desarrollo del curso (todos juntos en una sola demo)

&#x09;9 (solicitud desde web e inicio de instancia)

&#x09;2 (modificaciones a la propuesta)

&#x09;1 parcialmente (aceptación de la propuesta nueva)

&#x09;10 (acceso de empresa a su cuenta, confirmar que solo ve SUS cursos)

&#x09;11 (preparar materiales desde su propia app, emitir comunicaciones desde su propia app)

&#x09;6 (evaluar recursos, reservar aula fisica + contratar equipamiento, pero luego cancelar para mostrar las compensaciones)



&#x09;NO CONFECCIONAR LA LISTA DEFINITIVA, ASI NOS ASEGURAMOS DE QUE NO TERMINE SIN QUERER LA PREPARACION DE CURSO





* Escenarios varios con final normal del proceso

&#x09;1 parcialmente \[la otra parte] (finalizacion normal del curso y liberación de recursos)

&#x09;8 (definir el docente para el curso, que un administrativo reclame 'liberar equipamiento')

&#x09;

&#x09;**python scripts/escenarios/preparar\_escenario\_1.py**  # Crea una instancia que queda esperando en 'definir docente que realizara el curso'



* Escenario 7 (falta de aulas físicas)

&#x09;Requiere que no hayan quedado cursos pendientes de escenarios anteriores, y levantar de nuevo el docker con la variable de entorno AULAS\_DISPONIBLES en 3.



&#x09;**AULAS\_DISPONIBLES=3 docker compose up -d flowable**

&#x09;**until curl -sf -u admin:test http://localhost:8080/flowable-ui/process-api/repository/deployments >/dev/null; do sleep 5; done**

&#x09;**python scripts/escenarios/preparar\_escenario\_7.py**  # Crea 3 instancias con aulas físicas reservadas



&#x09;Avanzamos manualmente hasta reservar un aula física, que fallará. Cancelamos una instancia para liberar un aula, confirmamos que se liberó una para que vuelva a intentar reservar.



&#x09;**docker compose up -d flowable**  # Volver al default de 10 aulas disponibles

&#x09;**until curl -sf -u admin:test http://localhost:8080/flowable-ui/process-api/repository/deployments >/dev/null; do sleep 5; done**





* Escenario 5 (curso no viable por regla)

&#x09;**python scripts/escenarios/preparar\_escenario\_5.py**  # Crea 5 instancias ya habilitadas para que una nueva falle por no ser viable

&#x09;



AL FINAL DE TODO, SE PUEDE MOSTRAR EN LA APP DE MATERIALES LAS CONSULTAS HISTÓRICAS





#### **CHECKLIST PRE-DEFENSA:**

* Tener el VSCode abierto con las 4 terminales (main y web \[WSL], materiales y comms \[WINDOWS])
* Tener la otra terminal en WSL para los logs
* Resetear y levantar todo de 0, levantar apps, levantar logs, en sus terminales correspondientes
* Importar el modelo .bpmn al Modeler de Flowable para poder mostrar rápidamente dónde hay execution y task listeners, delegates, etc. En cuenta de admin/test para mostrar tambien los procesos vivos en la Task App -> Processes



















