#!/usr/bin/env python
import os
import sys
import random
import subprocess
import threading
import time

import re

MINIMUM_DIFFERENCE = 1.0
NUM_REQUESTS = [100]
NUM_CLIENTS = [20, 30, 40, 50]
PMIN = 2
PMAXS = [10, 20, 30, 40, 50]
# represents the addition of an item to a resource
end_test_cond = threading.Condition()
end_test = False


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
        handler = open("logs/clients_{num_clients}/req_{num_requests}/pmax_{pmax}/"
                       "client{clientid}.dat".format(num_clients=num_clients, num_requests=num_requests,
                                                     pmax=pmax, clientid=self.clientid), "w")

        error_handler = open("logs/clients_{num_clients}/req_{num_requests}/pmax_{pmax}/"
                             "client{clientid}.log".format(num_clients=num_clients, num_requests=num_requests,
                                                           pmax=pmax, clientid=self.clientid), "w")
        print "Client {clientid} ({pmin}, {pmax})".format(clientid=self.clientid, pmin=self.pmin, pmax=self.pmax)
        subprocess.call(args, stdout=handler, stderr=error_handler)
        handler.close()
        error_handler.close()


class Server(threading.Thread):
    def run(self):
        args = ["java", "-jar", "../demo-apps/run/cf-dynamic-period-server-1.1.0-SNAPSHOT.jar", "5685"]
        print " ".join(args)
        handler = open("logs/clients_{num_clients}/req_{num_requests}/pmax_{pmax}/"
                       "server.dat".format(num_clients=num_clients, num_requests=num_requests,
                                           pmax=pmax), "w")
        error_handler = open("logs/clients_{num_clients}/req_{num_requests}/pmax_{pmax}/"
                             "server.log".format(num_clients=num_clients, num_requests=num_requests,
                                                 pmax=pmax), "w")
        p = subprocess.Popen(args, stdout=handler, stderr=error_handler)
        end_test_cond.acquire()
        while not end_test:
            end_test_cond.wait(100000.0)
        end_test_cond.release()
        p.kill()


class Proxy(threading.Thread):
    def run(self):
        args = ["java", "-jar", "../demo-apps/run/cf-reverseproxy-1.1.0-SNAPSHOT.jar"]
        print " ".join(args)
        handler = open("logs/clients_{num_clients}/req_{num_requests}/pmax_{pmax}/"
                               "proxy.dat".format(num_clients=num_clients, num_requests=num_requests,
                                                  pmax=pmax), "w")
        error_handler = open("logs/clients_{num_clients}/req_{num_requests}/pmax_{pmax}/"
                             "proxy.log".format(num_clients=num_clients, num_requests=num_requests,
                                                pmax=pmax), "w")
        p = subprocess.Popen(args, stdout=handler, stderr=error_handler)
        end_test_cond.acquire()
        while not end_test:
            end_test_cond.wait(100000.0)
        end_test_cond.release()
        p.kill()


class RandomClients(object):
    def __init__(self, limit_pmin, limit_pmax, num_clients, num_requests):
        self.limit_pmin = limit_pmin
        self.limit_pmax = limit_pmax
        self.number_clients = num_clients
        self.num_requests = num_requests

    def create(self):
        client_threads = []
        for i in range(0, self.number_clients):
            pmin = 0
            pmax = -1
            while pmax <= (pmin + MINIMUM_DIFFERENCE):
                pmin = random.uniform(self.limit_pmin, self.limit_pmax)
                pmax = random.uniform(self.limit_pmin, self.limit_pmax)
                # pmin = random.randint(self.limit_pmin, self.limit_pmax)
                # pmax = random.randint(self.limit_pmin, self.limit_pmax)
            uri = "coap://127.0.0.1:5683/127.0.0.1:5685/InterfaceResource"
            client = Client(i, pmin, pmax, uri, self.num_requests, "clientlog" + str(i) + ".log")
            client.start()
            client_threads.append(pmax)
        # for t in client_threads:
        #     t.join()
        # sys.exit()
        return max(client_threads)

if __name__ == "__main__":
    for num_clients in NUM_CLIENTS:
        os.system("mkdir logs/clients_{num_clients}".format(num_clients=num_clients))
        for num_requests in NUM_REQUESTS:
            os.system("mkdir logs/clients_{num_clients}/req_{num_requests}"
                      .format(num_clients=num_clients, num_requests=num_requests))
            for pmax in PMAXS:
                os.system("mkdir logs/clients_{num_clients}/req_{num_requests}/pmax_{pmax}"
                          .format(num_clients=num_clients, num_requests=num_requests, pmax=pmax))
                # clients = RandomClients(2.0, 30.0, num_clients)
                restart = True
                seed = 1
                while restart:
                    restart = False
                    random.seed(seed)
                    clients = RandomClients(PMIN, pmax, num_clients, num_requests)

                    server = Server()
                    proxy = Proxy()
                    server.start()
                    proxy.start()
                    time.sleep(15)
                    max_pmax = clients.create()
                    time.sleep(max_pmax)
                    ended_clients = 0
                    while ended_clients != num_clients:
                        ended_clients = 0
                        for i in range(0, num_clients):
                            filename = "logs/clients_{num_clients}/req_{num_requests}/pmax_{pmax}/"\
                                       "client{clientid}.dat".format(num_clients=num_clients, num_requests=num_requests,
                                                                     pmax=pmax, clientid=i)
                            if os.stat(filename).st_size == 0:
                                # restart = True
                                # seed += 1
                                # folder = "logs/clients_{num_clients}/req_{num_requests}/pmax_{pmax}/"\
                                #     .format(num_clients=num_clients, num_requests=num_requests,
                                #             pmax=pmax)
                                # os.system("rm -r " + folder)
                                # ended_clients = num_clients
                                # break
                                ended_clients += 1
                                print 'Client ' + str(i) + " NOT STARTED"

                            with open(filename) as f:
                                line = ""
                                for line in f:
                                    values = line.split("TotalNotifications: ")
                                    if len(values) > 1:
                                        ended_clients += 1
                                        print 'Client ' + str(i) + " Finished"

                                last = line
                                try:
                                    values = last.split(" ")
                                    num_iter = int(values[3])
                                    print 'Client ' + str(i) + " iter: " + str(num_iter)
                                except:
                                    pass
                        print "-----------------------------------------------"
                        time.sleep(max_pmax)
                print "TEST END"
                end_test_cond.acquire()
                end_test = True
                end_test_cond.notifyAll()
                end_test_cond.release()
                time.sleep(10)
                end_test = False

    sys.exit()



