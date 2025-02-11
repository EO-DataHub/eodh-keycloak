# EO DataHub Keycloak

## Build

```bash
cd eodh-keycloak-plugins
mvn clean package
```

## Deploy

```bash
make build
make push
```

## Testing

### Docker

A local Docker build has been provided using Docker Compose.

```bash
docker compose up --build
docker compose down
```



# Changing Keycloak theme
First check out our forked version of keycloakify-starter
```bash 
https://github.com/EO-DataHub/keycloakify-starter
```
Run install commands in this newly checked out repo
```bash 
npm install
```
We want to test the styles that we are making inside storybook, this is already setup in the keycloakify-starter repo, we just need to add the stories. \
In keycloakify-starter run the command
```bash
npx keycloakify add-story
```
In the terminal you will get a list selection of which stories you would like to add, pick the story you want with the arrows and press enter. \
Then to see these stories in the browser, run the command 

```bash 
npm run storybook
```
You can now make changes to the CSS inside of `main.css`

### Building the theme

Now that the css changes have been made, we need to build this into a `.jar`. \
In the keycloakify-starter repo run the command
```bash
npm run build-keycloak-theme
```
This will create two `.jar` files inside of `dist_keycloak`, it may take a few minutes.

Now that we have the theme we want to copy it to this `eodhp-keycloak` repo. Firstly we need to find out the version of keycloak we are using. As of writing this we are using version `26.0.4` of keycloak.  

Copy over the version of the `.jar` which is for the correct version of keycloak and copy it into the root directory of this repo. Rename the file `theme.jar` overwriting the file that is already here. It must be named `theme.jar` as that is what the Dockerfile is expecting.

Now we need to create the docker image which is stores on ECR. \
Run the command
```bash
aws --profile eodhp-prod ecr-public get-login-password --region us-east-1 | docker login --username AWS --password-stdin public.ecr.aws/eodh
```

Then to make the image locally run 

```bash
make container-build version=26.0.4-0.2.2-rc2
```

Make sure to change the value of version to match the new version of the image you want to make.

Then to push the image to ECR run

```bash
make container-push version=26.0.4-0.2.2-rc2
```

### Deploying the theme
Now that we have the image we need to deploy it. Go to the `eodhp-argocd-deployment` repo and navigate to the file `apps/keycloak/envs/{ENVIRONMENT}/kustomization.yaml`. Here we want to replace what image keycloak is pointing to. Change the value of `spec/image` to the version of the image that you just made
```bash
- op: replace
    path: /spec/image
    value: public.ecr.aws/eodh/eodh-keycloak:26.0.4-0.2.2-rc2
```
Push this up.

Now we need to enable the theme in keycloak itself. Navigate to the master keycloak URL for example for dev3 that would be `https://dev3.eodhp.eco-ke-staging.com/keycloak`. And log in with the master credentials, the username is admin and you can get the password from the Secret Manager in AWS.

Once you have logged in, select from the dropdown in the top left `eodhp`, we do not want to change the master styling.

Then go to `Realm settings > Themes` and from the dropdowns select the `keycloakify-starter` option and save. This will then update the themes for the EODHP page.