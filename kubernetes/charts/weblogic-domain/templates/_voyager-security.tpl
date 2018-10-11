# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "domain.voyagerSecurity" }}
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: voyager-operator
  namespace: voyager
  labels:
    weblogic.resourceVersion: voyager-load-balancer-v1
    app: voyager

---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: voyager-operator
  namespace: voyager
  labels:
    weblogic.resourceVersion: voyager-load-balancer-v1
    app: voyager
rules:
- apiGroups:
  - apiextensions.k8s.io
  resources:
  - customresourcedefinitions
  verbs:
  - "*"
- apiGroups:
  - extensions
  resources:
  - thirdpartyresources
  verbs:
  - "*"
- apiGroups:
  - voyager.appscode.com
  resources: ["*"]
  verbs: ["*"]
- apiGroups:
  - monitoring.coreos.com
  resources:
  - servicemonitors
  verbs: ["get", "list", "watch", "create", "update", "patch"]
- apiGroups:
  - apps
  resources:
  - deployments
  verbs: ["*"]
- apiGroups:
  - extensions
  resources:
  - deployments
  - daemonsets
  - ingresses
  verbs: ["*"]
- apiGroups: [""]
  resources:
  - replicationcontrollers
  - services
  - endpoints
  - configmaps
  verbs: ["*"]
- apiGroups: [""]
  resources:
  - secrets
  verbs: ["get", "list", "watch", "create", "update", "patch"]
- apiGroups: [""]
  resources:
  - namespaces
  verbs: ["get", "list", "watch"]
- apiGroups: [""]
  resources:
  - events
  verbs: ["create"]
- apiGroups: [""]
  resources:
  - pods
  verbs: ["list", "watch", "delete", "deletecollection"]
- apiGroups: [""]
  resources:
  - nodes
  verbs: ["list", "watch", "get"]
- apiGroups: [""]
  resources:
  - serviceaccounts
  verbs: ["get", "create", "delete", "patch"]
- apiGroups:
  - rbac.authorization.k8s.io
  resources:
  - rolebindings
  - roles
  verbs: ["get", "create", "delete", "patch"]

---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: voyager-operator
  namespace: voyager
  labels:
    weblogic.resourceVersion: voyager-load-balancer-v1
    app: voyager
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: voyager-operator
subjects:
- kind: ServiceAccount
  name: voyager-operator
  namespace: voyager

---
# to read the config for terminating authentication
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: voyager-apiserver-extension-server-authentication-reader
  namespace: kube-system
  labels:
    weblogic.resourceVersion: voyager-load-balancer-v1
    app: voyager
roleRef:
  kind: Role
  apiGroup: rbac.authorization.k8s.io
  name: extension-apiserver-authentication-reader
subjects:
- kind: ServiceAccount
  name: voyager-operator
  namespace: voyager

---
# to delegate authentication and authorization
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: voyager-apiserver-auth-delegator
  labels:
    app: voyager
roleRef:
  kind: ClusterRole
  apiGroup: rbac.authorization.k8s.io
  name: system:auth-delegator
subjects:
- kind: ServiceAccount
  name: voyager-operator
  namespace: voyager

---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: appscode:voyager:edit
  labels:
    weblogic.resourceVersion: voyager-load-balancer-v1
    rbac.authorization.k8s.io/aggregate-to-admin: "true"
    rbac.authorization.k8s.io/aggregate-to-edit: "true"
rules:
- apiGroups:
  - voyager.appscode.com
  resources:
  - certificates
  - ingresses
  verbs:
  - create
  - delete
  - deletecollection
  - get
  - list
  - patch
  - update
  - watch

---
kind: ClusterRole
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: appscode:voyager:view
  labels:
    weblogic.resourceVersion: voyager-load-balancer-v1
    rbac.authorization.k8s.io/aggregate-to-view: "true"
rules:
- apiGroups:
  - voyager.appscode.com
  resources:
  - certificates
  - ingresses
  verbs:
  - get
  - list
  - watch
{{- end }}