import java.util.Random;
public class Event{
    int type;
    double scTime;
    double crTime;

    Node genNode;
    Node node;
    Block block;
    Transaction transaction;
    Random randomno;


    public Event(int type, double scTime, Node node, Block block, Transaction transaction, Node genNode){
        this.type = type;
        this.scTime = scTime;
        this.node = node;
        this.block = block;
        this.node = node;
        this.transaction = transaction;
        this.genNode = genNode;
        this.randomno = new Random();
    }

    void execute(Simulator s){
        switch(type){
            case 0:
                // Block generate
                generateBlock(s,crTime,scTime);
                break;
            case 1:
                // Transaction generate
                generateTransaction(s);
                break;
            case 2:
                // Block receive
                receiveBlock(s,scTime);
                break;
            case 3:
                // Transaction receive
                receiveTransaction(s, scTime);
                break;

        }
    }

    void generateTransaction(Simulator s){
      int toID = node.id;
      while(toID == node.id){
        toID = randomno.nextInt(s.n);
      }
      float currCoins = s.nodes.get(node.id).coins;
      float fraction = randomno.nextFloat();
      float transactionAmt = currCoins*fraction;
      Transaction newTransaction = new Transaction(s.currID, node.id, toID, transactionAmt);
      s.currID++;
      s.nodes.get(node.id).coins -= transactionAmt;
      s.nodes.get(toID).coins += transactionAmt;

      //add transaction to current node's list
      s.transactions.get(node.id).add(newTransaction);

      //create next transaction event for this node
      double lambda = 10;   //arbit value
      double t = Math.log(1-Math.random())/(-lambda);
      Event nextTransactionEvent = new Event(1, scTime + t, node, null, null, node);
      nextTransactionEvent.crTime = scTime;
      s.queue.add(nextTransactionEvent);

      //create next receive event for its neighbours
      if(s.nodes.get(node.id).peers == null)
        System.out.println("Peers is NUll" + String.valueOf(node.id));

      int size = s.nodes.get(node.id).peers.size();
      for(int i=0; i<size; i++){
        Event receiveTransactionEvent;
        double latency = s.simulateLatency(node.id, s.nodes.get(node.id).peers.get(i).id, 0);
        receiveTransactionEvent = new Event(3, scTime+latency, s.nodes.get(node.id).peers.get(i), null, newTransaction, node);  //take receive event constructor
        receiveTransactionEvent.crTime = scTime;
        s.queue.add(receiveTransactionEvent);
      }
    }

    void receiveTransaction(Simulator s, double scheduledTime){
        boolean found = false;
        for(int i=0; i<s.transactions.get(node.id).size(); i++){
            Transaction t = s.transactions.get(node.id).get(i);
            if(t.tID == transaction.tID){
                found = true;
                break;
            }
        }
        if(!found){
            Transaction tr = new Transaction(transaction);
            s.transactions.get(node.id).add(tr);
            for(int i=0; i<node.peers.size(); i++){
                if(node.peers.get(i).id != genNode.id){
                    double latency = s.simulateLatency(node.id, node.peers.get(i).id, 0);
                    Event e = new Event(3, scheduledTime+latency, node.peers.get(i), null, tr, node);
                    e.crTime = scTime;
                    s.queue.add(e);
                }
            }
        }
    }

    void receiveBlock(Simulator s, double scheduledTime){
        boolean found = false;
        for(int i=0; i<s.blocks.get(node.id).size(); i++){
            Block b = s.blocks.get(node.id).get(i);
            if(b.bID == block.bID){
                found = true;
                break;
            }
        }

        if(!found){
            double v = (new Random()).nextDouble();
            double T_k = Math.log(1 - v)/(-node.lambda);

            int len = 0;
            Block blk = new Block();
            for(int i=0; i<s.blocks.get(node.id).size(); i++){
                if(s.blocks.get(node.id).get(i).bID == block.previousBlock.bID){
                    len = s.blocks.get(node.id).get(i).length;
                    blk = s.blocks.get(node.id).get(i);
                    break;
                }
            }

            Block blockReceived = new Block(block);      //Added Copy constructor()
            blockReceived.length = len+1;
            blockReceived.previousBlock = blk;
            blockReceived.timestamp = s.currTime;
            s.blocks.get(node.id).add(blockReceived);
            s.nodes.get(node.id).receivedStamps.add(scheduledTime);

            for(int i=0; i<node.peers.size(); i++){
                if(node.peers.get(i).id != genNode.id){
                    double latency = s.simulateLatency(node.id, node.peers.get(i).id, 1);
                    Event e = new Event(2, scheduledTime+latency, node.peers.get(i), blockReceived, null, node);
                    e.crTime = scTime;
                    s.queue.add(e);
                }
            }

            Event e = new Event(0, scheduledTime+T_k, node, null, null, node);
            e.crTime = scTime;
            s.queue.add(e);
        }
    }

    void generateBlock(Simulator s, double creationTime, double scheduledTime){
        Block blk;
        boolean found = false;
        for(int i = 0; i < node.receivedStamps.size(); i++ ){
            if(node.receivedStamps.get(i) < scheduledTime && node.receivedStamps.get(i) > creationTime){
                found  = true;
                break;
            }
        }
        if(!found){
            int len = 0;
            Block id = new Block();
            for(int i=0;i<s.blocks.get(node.id).size();i++){
                Block b = s.blocks.get(node.id).get(i);
                if(b.length > len){
                    len = b.length;
                    id = b;
                }
            }

            blk = new Block(s.genBlockID(),scheduledTime, id, node.id, len + 1);
//            System.out.println("New Block ID: " + Integer.toString(blk.bID) + " Creator:" + Integer.toString(blk.creatorID) + " Length: " + Integer.toString(blk.length));
            for(int i = 0;i < s.transactions.get(node.id).size();i++){
                Transaction t = s.transactions.get(node.id).get(i);

                int len1 = 0;
                Block lastBlock = new Block();
                for(int j=0; j<s.blocks.get(node.id).size(); j++){
                    if(s.blocks.get(node.id).get(j).length > len1){
                        len1 = s.blocks.get(node.id).get(j).length;
                        lastBlock = s.blocks.get(node.id).get(j);
                    }
                }


                if(lastBlock.transactions.contains(t)){
                    s.transactions.get(node.id).remove(i);
                }
                else{
                    blk.transactions.add(t);
                }
            }
            s.blocks.get(node.id).add(blk);
            broadcastBlock(s, blk, scheduledTime);
        }
    }

    void broadcastBlock(Simulator s, Block blk, double scheduledTime){
        for(int i=0; i<node.peers.size(); i++){
            double latency = s.simulateLatency(node.id, node.peers.get(i).id, 1);
            Event e = new Event(2, scheduledTime+latency, node.peers.get(i), blk, null, node);
            e.crTime = scTime;
            s.queue.add(e);
        }
    }
}
