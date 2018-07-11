{{- define "operator.elasticSearchDeployment" }}
{{- if .elkIntegrationEnabled }}
---
apiVersion: apps/v1beta1
kind: Deployment
metadata:
  name: elasticsearch
  labels:
    app: elasticsearch
spec:
  replicas: 1
  selector:
    matchLabels:
      app: elasticsearch
  template:
    metadata:
      labels:
        app: elasticsearch
    spec:
      containers:
      - name: elasticsearch
        image: elasticsearch:5
        ports:
        - containerPort: 9200
        - containerPort: 9300
{{- end }}
{{- end }}