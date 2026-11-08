# Despliegue en GCP desde cero

Guía paso a paso para desplegar la API en Google Cloud con:
- **Cloud Run** (la app)
- **Cloud SQL** (PostgreSQL 18)
- **Secret Manager** (llaves y contraseñas)
- **Artifact Registry** (imágenes Docker)
- **GitHub Actions** con **Workload Identity Federation** (despliegue continuo sin llaves JSON)

Todos los comandos son reproducibles. **Ningún secreto aparece en esta guía ni en el repositorio**: se generan en la terminal y van directo a Secret Manager.

```
GitHub (push a main) ──WIF──► Artifact Registry ──► Cloud Run ──Cloud SQL connector (IAM + TLS)──► Cloud SQL PG18
                                                       │  └── Secret Manager (variables de entorno)
                                                       └── SMTP 587 STARTTLS ──► Brevo ──► correo del cliente
```

## 0. Requisitos
- Un proyecto de GCP con facturación vinculada (aquí: `tokenization-system`).
- `gcloud` (Google Cloud SDK), Docker con `buildx` y `openssl`.
- Una cuenta SMTP transaccional (aquí: **Brevo**, con un remitente verificado y una *SMTP key*).

### Variables usadas en toda la guía
```bash
export PROJECT_ID=tokenization-system
export REGION=us-central1
export SQL_INSTANCE=tokenization-db
export DB_NAME=tokenization
export DB_USER=app
export RUN_SA=tokenization-run@${PROJECT_ID}.iam.gserviceaccount.com
export IMAGE=${REGION}-docker.pkg.dev/${PROJECT_ID}/app/payment-tokenization-system
export SERVICE=payment-tokenization-system
```

## 1. Configuración aislada de gcloud
Se usa una configuración propia para no tocar otras cuentas o proyectos de la máquina:
```bash
gcloud config configurations create tokenization --no-activate
export CLOUDSDK_ACTIVE_CONFIG_NAME=tokenization        # solo para esta terminal
gcloud auth login                                      # si la cuenta aún no está autenticada
gcloud config set account <tu-cuenta>@gmail.com
gcloud config set project $PROJECT_ID
gcloud config set run/region $REGION
gcloud billing projects describe $PROJECT_ID --format="value(billingEnabled)"   # debe decir True
```

## 2. Activar las APIs
```bash
gcloud services enable run.googleapis.com sqladmin.googleapis.com secretmanager.googleapis.com \
  artifactregistry.googleapis.com iam.googleapis.com iamcredentials.googleapis.com sts.googleapis.com
```

## 3. Base de datos: Cloud SQL PostgreSQL 18
```bash
gcloud sql instances create $SQL_INSTANCE --database-version=POSTGRES_18 --edition=enterprise \
  --tier=db-f1-micro --region=$REGION --availability-type=zonal \
  --storage-type=SSD --storage-size=10GB --backup-start-time=07:00
gcloud sql databases create $DB_NAME --instance=$SQL_INSTANCE
```
- Tarda unos 10 minutos.
- `db-f1-micro` en una sola zona es lo mínimo para un reto. En producción: `db-custom-*`, alta disponibilidad (`--availability-type=regional`) y recuperación a un punto en el tiempo (PITR).
- El esquema **no** se crea a mano: lo crea **Flyway** al arrancar la app (migraciones `V1..V4`).

### Usuario de la aplicación
La contraseña se genera y se guarda en Secret Manager sin mostrarse nunca en pantalla:
```bash
DB_PASSWORD=$(openssl rand -base64 30 | tr -d '/+=\n')
gcloud sql users create $DB_USER --instance=$SQL_INSTANCE --password="$DB_PASSWORD"
printf %s "$DB_PASSWORD" | gcloud secrets create db-password --replication-policy=automatic --data-file=-
unset DB_PASSWORD
```

## 4. Secretos de la aplicación
Se generan aleatoriamente y van directo a Secret Manager:
```bash
openssl rand -base64 36 | tr -d '/+=\n' | gcloud secrets create api-key             --replication-policy=automatic --data-file=-
openssl rand -base64 48 | tr -d '\n'    | gcloud secrets create jwt-secret          --replication-policy=automatic --data-file=-
openssl rand -base64 32 | tr -d '\n'    | gcloud secrets create card-encryption-key --replication-policy=automatic --data-file=-
```
Las credenciales SMTP se escriben a mano. `read -s` no las muestra en pantalla. Hazlo en una terminal propia, porque estos comandos piden texto:
```bash
printf "SMTP login (Brevo): "; read -r SMTP_USERNAME; printf %s "$SMTP_USERNAME" | gcloud secrets create smtp-username --replication-policy=automatic --data-file=-
printf "SMTP key (Brevo): ";   read -rs SMTP_PASSWORD; echo; printf %s "$SMTP_PASSWORD" | gcloud secrets create smtp-password --replication-policy=automatic --data-file=-
unset SMTP_USERNAME SMTP_PASSWORD
```

| Secreto | Variable de entorno en Cloud Run |
|---|---|
| `db-password` | `DB_PASSWORD` |
| `api-key` | `API_KEY` |
| `jwt-secret` | `JWT_SECRET` (≥ 32 bytes) |
| `card-encryption-key` | `CARD_ENCRYPTION_KEY` (AES-256, 32 bytes en base64) |
| `smtp-username` / `smtp-password` | `SMTP_USERNAME` / `SMTP_PASSWORD` |

Para rotar un secreto se agrega una versión nueva (`gcloud secrets versions add <secreto> --data-file=-`) y se redespliega. La llave AES se rota con `key_id`; ver ADR-004.

## 5. Registro de imágenes y cuenta de servicio de la app
```bash
gcloud artifacts repositories create app --repository-format=docker --location=$REGION \
  --description="Imagenes de payment-tokenization-system"

gcloud iam service-accounts create tokenization-run --display-name="Cloud Run runtime (payment-tokenization-system)"
gcloud projects add-iam-policy-binding $PROJECT_ID --member=serviceAccount:$RUN_SA --role=roles/cloudsql.client --condition=None
gcloud projects add-iam-policy-binding $PROJECT_ID --member=serviceAccount:$RUN_SA --role=roles/secretmanager.secretAccessor --condition=None
```
La app corre con su **propia cuenta de servicio y solo los permisos mínimos**: conectarse a Cloud SQL y leer secretos. No usa la cuenta por defecto de Compute, que tiene rol de *Editor*.

## 6. Primera imagen
Cloud Run ejecuta imágenes `linux/amd64`. En una Mac con Apple Silicon hay que indicar la plataforma:
```bash
gcloud auth configure-docker ${REGION}-docker.pkg.dev
docker buildx build --platform linux/amd64 -t ${IMAGE}:manual-1 --push .
```

## 7. Desplegar en Cloud Run
Las variables se pasan en un solo `--set-env-vars`, separadas por `|`. La sintaxis `^|^` cambia el separador, porque los valores contienen `,` `@` `&` y `=`.
```bash
MAIL_FROM=<remitente-verificado-en-brevo>
ENVS="^|^SPRING_PROFILES_ACTIVE=prod|DB_USERNAME=${DB_USER}|SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=5\
|DB_URL=jdbc:postgresql:///${DB_NAME}?cloudSqlInstance=${PROJECT_ID}:${REGION}:${SQL_INSTANCE}&socketFactory=com.google.cloud.sql.postgres.SocketFactory\
|SMTP_HOST=smtp-relay.brevo.com|SMTP_PORT=587|SMTP_AUTH=true|SMTP_STARTTLS=true|MAIL_FROM=${MAIL_FROM}\
|PAYMENT_REJECTION_PROBABILITY=0.3|PAYMENT_MAX_RETRIES=3|PAYMENT_RETRY_BACKOFF=200ms\
|TOKENIZATION_REJECTION_PROBABILITY=0.1|PRODUCT_MIN_STOCK=0"

gcloud run deploy $SERVICE --image=${IMAGE}:manual-1 --region=$REGION \
  --service-account=$RUN_SA \
  --add-cloudsql-instances=${PROJECT_ID}:${REGION}:${SQL_INSTANCE} \
  --no-allow-unauthenticated \
  --cpu=1 --memory=1Gi --cpu-boost --no-cpu-throttling --min-instances=1 --max-instances=3 --concurrency=80 \
  --set-env-vars="$ENVS" \
  --set-secrets="DB_PASSWORD=db-password:latest,API_KEY=api-key:latest,JWT_SECRET=jwt-secret:latest,CARD_ENCRYPTION_KEY=card-encryption-key:latest,SMTP_USERNAME=smtp-username:latest,SMTP_PASSWORD=smtp-password:latest"
```
- **Secretos:** `--set-secrets` hace que Cloud Run lea Secret Manager al arrancar la instancia y exponga cada valor como variable de entorno. Los valores no aparecen en el comando ni en la consola.
- **Conexión a la BD:** el *Cloud SQL Java Connector* (`postgres-socket-factory`) cifra la conexión con TLS y la autoriza con IAM (rol `cloudsql.client`). No hay que abrir IPs.
- **Esquema:** en el primer arranque, Flyway aplica `V1..V4` (tablas, índices y los 40 productos del seed).
- **Pool de conexiones:** 5 conexiones × 3 instancias = 15, por debajo del límite de `db-f1-micro` (~25).
- **`--no-cpu-throttling` (CPU siempre asignada):** por defecto Cloud Run solo da CPU **mientras atiende una petición**. Esta app hace trabajo **después** de responder: correos, auditoría y el job que reintenta eventos fallidos cada minuto. Sin este flag ese trabajo se frena (medido: el correo tardaba más de 6 s, y sin tráfico los reintentos no corrían). Con él, el correo queda auditado en ~2 s. Ver ADR-012.
- **`--min-instances=1`:** siempre hay una instancia lista. Elimina el arranque en frío y mantiene vivo el job de reintentos. Costo aproximado: ~12 USD/semana. Fuera de un periodo de evaluación se puede volver a `--min-instances=0`: los eventos pendientes se procesan igual al arrancar la siguiente instancia (`republish-outstanding-events-on-restart`).
- `--min-instances=1` **sin** `--no-cpu-throttling` no basta: la instancia existe, pero con la CPU frenada entre peticiones.

### Hacer la API pública
Se despliega primero privada para verificarla. Después se permite invocarla sin identidad de Google, porque la seguridad la hace la propia app (API key + JWT):
```bash
gcloud run services add-iam-policy-binding $SERVICE --region=$REGION --member=allUsers --role=roles/run.invoker
```

### Verificar
```bash
URL=$(gcloud run services describe $SERVICE --region=$REGION --format="value(status.url)")
curl -s $URL/ping                       # pong
curl -s $URL/actuator/health            # {"status":"UP"}
npx newman run postman/payment-tokenization.postman_collection.json \
  -e postman/gcp.postman_environment.json --env-var "baseUrl=$URL" \
  --env-var "apiKey=$(gcloud secrets versions access latest --secret=api-key)"
```

## 8. Despliegue continuo con GitHub Actions (sin llaves JSON)
GitHub Actions obtiene credenciales de GCP de corta duración intercambiando su token OIDC (**Workload Identity Federation**). No hay ninguna llave JSON que pueda filtrarse.

```bash
export PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format="value(projectNumber)")
export DEPLOYER_SA=github-deployer@${PROJECT_ID}.iam.gserviceaccount.com
export REPO=AndyNeri/payment-tokenization-system

# Identidad que usa el pipeline
gcloud iam service-accounts create github-deployer --display-name="GitHub Actions deployer (payment-tokenization-system)"

# Pool + proveedor OIDC: solo acepta tokens de ESTE repositorio y de la rama main
gcloud iam workload-identity-pools create github --location=global --display-name="GitHub Actions"
gcloud iam workload-identity-pools providers create-oidc github-repo --location=global \
  --workload-identity-pool=github --display-name="GitHub repo (main)" \
  --issuer-uri=https://token.actions.githubusercontent.com \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref" \
  --attribute-condition="assertion.repository=='${REPO}' && assertion.ref=='refs/heads/main'"

# El repositorio puede actuar como github-deployer
gcloud iam service-accounts add-iam-policy-binding $DEPLOYER_SA --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github/attribute.repository/${REPO}"

# Permisos mínimos del pipeline
gcloud artifacts repositories add-iam-policy-binding app --location=$REGION \
  --member=serviceAccount:$DEPLOYER_SA --role=roles/artifactregistry.writer         # subir imágenes (solo a este repo)
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member=serviceAccount:$DEPLOYER_SA --role=roles/run.developer --condition=None   # desplegar revisiones
gcloud iam service-accounts add-iam-policy-binding $RUN_SA \
  --member=serviceAccount:$DEPLOYER_SA --role=roles/iam.serviceAccountUser          # desplegar "como" la cuenta de la app
```

El workflow `.github/workflows/cd.yml`:
1. Se dispara cuando **CI termina en verde en `main`** (es decir, después de mergear un PR). También se puede lanzar a mano.
2. Se autentica con WIF.
3. Construye la imagen `linux/amd64` y la sube a Artifact Registry, etiquetada con el SHA del commit.
4. Despliega una revisión nueva **cambiando solo la imagen**. Las variables, secretos y la conexión a Cloud SQL se configuraron una vez en el § 7.
5. Hace un *smoke test*: `/ping`, `/actuator/health` y la carpeta *Health* de Postman contra la URL real.

| Permiso del pipeline | Por qué |
|---|---|
| `artifactregistry.writer` (solo en el repo `app`) | subir la imagen |
| `run.developer` | crear revisiones; no puede cambiar quién invoca el servicio |
| `iam.serviceAccountUser` (solo sobre `tokenization-run`) | la revisión corre con la cuenta de la app |

## 9. Costos y limpieza
- **Cloud SQL `db-f1-micro`**: unos 9–10 USD al mes (es el único costo fijo).
- **Cloud Run** con `min-instances=0`, **Secret Manager** y **Artifact Registry**: prácticamente gratis con este uso.

Para apagar o borrar todo:
```bash
gcloud sql instances patch $SQL_INSTANCE --activation-policy=NEVER      # apagar la BD (deja de cobrar la CPU)
gcloud run services delete $SERVICE --region=$REGION
gcloud sql instances delete $SQL_INSTANCE
```
