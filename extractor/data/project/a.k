schema App:
    replicas: int
    a : int
    b : int

#for a
schema cdd :
    a : int

#for b
app = App {
    replicas = 2
    a = 2
}

app1 = App {
    replicas = 2
    a = 2
}

#for c
c = cdd {
    a = 1
}
