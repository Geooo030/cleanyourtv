/** Explicit, serialized mutations: failed requests never change the local library. */
export class FavoriteActions {
  constructor(api, generation, changed) { this.api=api; this.generation=generation; this.changed=changed; this.tail=Promise.resolve(); }
  set(id, liked) {
    if(!Number.isSafeInteger(id)||id<=0||typeof liked!=='boolean')return Promise.reject(Error('INVALID_FAVORITE'));
    const epoch=this.generation();
    const job=this.tail.catch(()=>{}).then(async()=>{
      if(epoch!==this.generation())throw Error('LOGIN_REQUIRED');
      const body=await this.api('like',{id,like:String(liked)});
      if(epoch!==this.generation())throw Error('LOGIN_REQUIRED');
      if(body?.code!==200)throw Error('FAVORITE_FAILED');
      this.changed(id,liked);
      return {id,liked};
    });
    this.tail=job; return job;
  }
}
