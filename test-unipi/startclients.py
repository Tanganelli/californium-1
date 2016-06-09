import random
import subprocess
import threading
import time

MINIMUM_DIFFERENCE = 1.0


class Client(threading.Thread):
    def __init__(self, clientid, pmin, pmax, uri, number=100, logging="clientout.log", local_ip="127.0.0.1"):
        super(Client, self).__init__()
        self.clientid = clientid
        self.pmin = "{0:.2f}".format(pmin)
        self.pmax = "{0:.2f}".format(pmax)
        self.uri = uri
        self.number = number
        self.logging = logging
        self.local_ip = local_ip
        self.setName("Client" + str(clientid))

    def run(self):

        args = ["java", "-jar", "../demo-apps/run/cf-interface-client-1.1.0-SNAPSHOT.jar", "-i", str(self.local_ip), "-l",
                str(self.logging), "-n", str(self.number), "-pmin", str(self.pmin), "-pmax", str(self.pmax),
                "-u", str(self.uri)]
        handler = open("logs/client{clientid}.dat".format(clientid=self.clientid), "w")
        error_handler = open("logs/client{clientid}.log".format(clientid=self.clientid), "w")
        print "Client {clientid} ({pmin}, {pmax})".format(clientid=self.clientid, pmin=self.pmin, pmax=self.pmax)
        subprocess.call(args, stdout=handler, stderr=error_handler)
        handler.close()
        error_handler.close()


class Server(threading.Thread):
    def run(self):
        args = ["java", "-jar", "../demo-apps/run/cf-interface-server-1.1.0-SNAPSHOT.jar", "5685"]
        print " ".join(args)
        handler = open("logs/server.dat", "w")
        error_handler = open("logs/server.log", "w")
        subprocess.call(args, stdout=handler, stderr=error_handler)
        handler.close()
        error_handler.close()


class Proxy(threading.Thread):
    def run(self):
        args = ["java", "-jar", "../demo-apps/run/cf-reverseproxy-1.1.0-SNAPSHOT.jar"]
        print " ".join(args)
        handler = open("logs/proxy.dat", "w")
        error_handler = open("logs/proxy.log", "w")
        subprocess.call(args, stdout=handler, stderr=error_handler)
        handler.close()
        error_handler.close()


class RandomClients(object):
    def __init__(self, limit_pmin, limit_pmax, num_clients):
        self.limit_pmin = limit_pmin
        self.limit_pmax = limit_pmax
        self.number_clients = num_clients

    def create(self):
        random.seed(1)
        for i in range(0, self.number_clients):
            pmin = 0
            pmax = -1
            while pmax <= (pmin + MINIMUM_DIFFERENCE):
                pmin = random.uniform(self.limit_pmin, self.limit_pmax)
                pmax = random.uniform(self.limit_pmin, self.limit_pmax)
                # pmin = random.randint(self.limit_pmin, self.limit_pmax)
                # pmax = random.randint(self.limit_pmin, self.limit_pmax)
            uri = "coap://127.0.0.1:5683/127.0.0.1:5685/InterfaceResource"
            client = Client(i, pmin, pmax, uri, 100, "clientlog" + str(i) + ".log")
            client.start()

if __name__ == "__main__":
    server = Server()
    proxy = Proxy()
    num_clients = 10
    # clients = RandomClients(2.0, 30.0, num_clients)
    clients = RandomClients(2, 30, num_clients)
    server.start()
    proxy.start()
    time.sleep(15)
    clients.create()




